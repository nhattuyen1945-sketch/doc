import CarPlay
import Combine

/// Giao diện trên màn hình xe. CarPlay chỉ cho app nhạc dùng template có sẵn (danh sách, tab, Now Playing)
/// và không có bàn phím, nên tìm kiếm trên xe dùng các từ khóa "Tìm nhanh" đã lưu từ điện thoại.
/// Bản đồ (Google Maps / VietMap) vẫn chạy song song, nhạc hiện ở Dashboard bên cạnh bản đồ.
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {

    private var ic: CPInterfaceController?
    private var bag = Set<AnyCancellable>()
    private let store = LibraryStore.shared
    private let player = PlayerManager.shared

    private lazy var playlistTpl = tab("Playlist", "music.note.list")
    private lazy var recentTpl = tab("Gần đây", "clock")
    private lazy var quickTpl = tab("Tìm nhanh", "magnifyingglass")

    func templateApplicationScene(_ scene: CPTemplateApplicationScene, didConnect ic: CPInterfaceController) {
        self.ic = ic
        ic.setRootTemplate(CPTabBarTemplate(templates: [playlistTpl, recentTpl, quickTpl]), animated: false, completion: nil)

        store.$playlist.receive(on: RunLoop.main).sink { [weak self] tracks in
            guard let self else { return }
            playlistTpl.updateSections([section(tracks, empty: "Thêm bài vào playlist trên điện thoại")])
        }.store(in: &bag)
        store.$recents.receive(on: RunLoop.main).sink { [weak self] tracks in
            guard let self else { return }
            recentTpl.updateSections([section(tracks, empty: "Chưa nghe bài nào")])
        }.store(in: &bag)
        store.$savedQueries.receive(on: RunLoop.main).sink { [weak self] queries in
            guard let self else { return }
            let items = queries.map { q -> CPListItem in
                let item = CPListItem(text: q, detailText: nil, image: UIImage(systemName: "magnifyingglass"))
                item.accessoryType = .disclosureIndicator
                item.handler = { [weak self] _, done in self?.openQuery(q); done() }
                return item
            }
            quickTpl.updateSections([CPListSection(items: items)])
        }.store(in: &bag)
    }

    func templateApplicationScene(_ scene: CPTemplateApplicationScene, didDisconnectInterfaceController ic: CPInterfaceController) {
        bag.removeAll()
        self.ic = nil
    }

    // MARK: -

    private func tab(_ title: String, _ symbol: String) -> CPListTemplate {
        let t = CPListTemplate(title: title, sections: [])
        t.tabTitle = title
        t.tabImage = UIImage(systemName: symbol)
        return t
    }

    private func section(_ tracks: [Track], empty: String) -> CPListSection {
        guard !tracks.isEmpty else { return CPListSection(items: [CPListItem(text: empty, detailText: nil)]) }
        let items = tracks.enumerated().map { i, t -> CPListItem in
            let item = CPListItem(text: t.title, detailText: t.artist, image: UIImage(systemName: "music.note"))
            item.handler = { [weak self] _, done in
                self?.player.play(tracks, startAt: i)
                self?.showNowPlaying()
                done()
            }
            Task { if let img = await player.artwork(for: t.artworkURL) { item.setImage(img) } }
            return item
        }
        return CPListSection(items: items)
    }

    private func openQuery(_ q: String) {
        let tpl = CPListTemplate(title: q, sections: [CPListSection(items: [CPListItem(text: "Đang tìm…", detailText: nil)])])
        ic?.pushTemplate(tpl, animated: true, completion: nil)
        Task {
            do {
                let tracks = try await player.source.search(q)
                tpl.updateSections([section(tracks, empty: "Không có kết quả")])
            } catch {
                tpl.updateSections([CPListSection(items: [CPListItem(text: "Lỗi", detailText: error.localizedDescription)])])
            }
        }
    }

    private func showNowPlaying() {
        let np = CPNowPlayingTemplate.shared
        if ic?.topTemplate !== np { ic?.pushTemplate(np, animated: true, completion: nil) }
    }
}
