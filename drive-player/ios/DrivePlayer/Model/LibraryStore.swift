import Foundation

/// Playlist, bài nghe gần đây, từ khóa "Tìm nhanh" và API key — lưu trong UserDefaults.
@MainActor
final class LibraryStore: ObservableObject {
    static let shared = LibraryStore()

    @Published private(set) var playlist: [Track] = [] { didSet { save(playlist, "playlist") } }
    @Published private(set) var recents: [Track] = [] { didSet { save(recents, "recents") } }
    @Published private(set) var savedQueries: [String] = [] { didSet { save(savedQueries, "queries") } }
    @Published var apiKey: String = "" { didSet { defaults.set(apiKey, forKey: "apiKey") } }

    private let defaults = UserDefaults.standard

    private init() {
        playlist = load("playlist") ?? []
        recents = load("recents") ?? []
        savedQueries = load("queries") ?? ["nhạc trẻ hot", "lofi chill", "bolero"]
        apiKey = defaults.string(forKey: "apiKey") ?? ""
    }

    func addToPlaylist(_ t: Track) { if !playlist.contains(where: { $0.id == t.id }) { playlist.append(t) } }
    func removeFromPlaylist(_ t: Track) { playlist.removeAll { $0.id == t.id } }
    func markPlayed(_ t: Track) { recents = Array(([t] + recents.filter { $0.id != t.id }).prefix(50)) }
    func addQuery(_ q: String) {
        let q = q.trimmingCharacters(in: .whitespaces)
        if !q.isEmpty && !savedQueries.contains(q) { savedQueries.append(q) }
    }
    func removeQuery(_ q: String) { savedQueries.removeAll { $0 == q } }

    private func save<T: Encodable>(_ v: T, _ key: String) {
        defaults.set(try? JSONEncoder().encode(v), forKey: key)
    }
    private func load<T: Decodable>(_ key: String) -> T? {
        defaults.data(forKey: key).flatMap { try? JSONDecoder().decode(T.self, from: $0) }
    }
}
