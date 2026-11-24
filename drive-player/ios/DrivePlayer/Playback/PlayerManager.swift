import AVFoundation
import MediaPlayer
import UIKit

/// Trình phát dùng chung cho cả màn hình điện thoại và CarPlay.
/// Link stream chỉ được lấy khi tới lượt bài đó phát, nên link không bị hết hạn khi nằm trong hàng chờ.
@MainActor
final class PlayerManager: ObservableObject {
    static let shared = PlayerManager()

    @Published private(set) var queue: [Track] = []
    @Published private(set) var index = 0
    @Published private(set) var isPlaying = false
    @Published private(set) var lastError: String?

    var current: Track? { queue.indices.contains(index) ? queue[index] : nil }

    let source: AudioSource = YouTubeSource(apiKey: { UserDefaults.standard.string(forKey: "apiKey") ?? "" })
    private let player = AVPlayer()
    private var loadTask: Task<Void, Never>?
    private var artworkCache: [URL: UIImage] = [:]

    private init() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default)
        try? session.setActive(true)

        NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.next() }
        }
        // Maps đọc chỉ đường → hệ thống ngắt/giảm âm; xong thì phát tiếp.
        NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  AVAudioSession.InterruptionType(rawValue: raw) == .ended,
                  let opts = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt,
                  AVAudioSession.InterruptionOptions(rawValue: opts).contains(.shouldResume)
            else { return }
            MainActor.assumeIsolated { self?.resume() }
        }
        setupRemoteCommands()
    }

    // MARK: - Điều khiển

    func play(_ tracks: [Track], startAt i: Int) {
        guard tracks.indices.contains(i) else { return }
        queue = tracks
        load(i)
    }

    func toggle() { isPlaying ? pause() : resume() }

    func resume() {
        if player.currentItem == nil, current != nil { load(index); return }
        player.play(); isPlaying = true; updateNowPlaying()
    }

    func pause() { player.pause(); isPlaying = false; updateNowPlaying() }

    func next() { if index + 1 < queue.count { load(index + 1) } else { pause() } }

    func previous() {
        if player.currentTime().seconds > 3 || index == 0 { player.seek(to: .zero) } else { load(index - 1) }
    }

    private func load(_ i: Int, attempt: Int = 0) {
        index = i
        loadTask?.cancel()
        player.pause()
        guard let track = current else { return }
        isPlaying = true
        lastError = nil
        updateNowPlaying()
        loadTask = Task {
            do {
                let url = try await source.streamURL(for: track.id)
                try Task.checkCancellation()
                player.replaceCurrentItem(with: AVPlayerItem(url: url))
                player.play()
                LibraryStore.shared.markPlayed(track)
                updateNowPlaying()
                loadArtwork(for: track)
            } catch {
                if Task.isCancelled { return } // đã chuyển sang bài khác
                lastError = "\(track.title): \(error.localizedDescription)"
                // Bài lỗi → bỏ qua sang bài tiếp
                if index + 1 < queue.count { load(index + 1) } else { isPlaying = false; updateNowPlaying() }
            }
        }
    }

    // MARK: - Màn hình khóa / CarPlay Now Playing / nút trên vô lăng

    private func setupRemoteCommands() {
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.resume() }; return .success }
        c.pauseCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.pause() }; return .success }
        c.togglePlayPauseCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.toggle() }; return .success }
        c.nextTrackCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.next() }; return .success }
        c.previousTrackCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.previous() }; return .success }
        c.changePlaybackPositionCommand.addTarget { [weak self] e in
            guard let e = e as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            MainActor.assumeIsolated { self?.player.seek(to: CMTime(seconds: e.positionTime, preferredTimescale: 600)) }
            return .success
        }
    }

    private func updateNowPlaying() {
        guard let t = current else { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil; return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: t.title,
            MPMediaItemPropertyArtist: t.artist,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: player.currentTime().seconds.isFinite ? player.currentTime().seconds : 0,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyPlaybackQueueIndex: index,
            MPNowPlayingInfoPropertyPlaybackQueueCount: queue.count,
        ]
        if let d = player.currentItem?.asset.duration.seconds, d.isFinite { info[MPMediaItemPropertyPlaybackDuration] = d }
        if let url = t.artworkURL, let img = artworkCache[url] {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: img.size) { _ in img }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        MPNowPlayingInfoCenter.default().playbackState = isPlaying ? .playing : .paused
    }

    func artwork(for url: URL?) async -> UIImage? {
        guard let url else { return nil }
        if let img = artworkCache[url] { return img }
        guard let (data, _) = try? await URLSession.shared.data(from: url), let img = UIImage(data: data) else { return nil }
        artworkCache[url] = img
        return img
    }

    private func loadArtwork(for t: Track) {
        Task { if await artwork(for: t.artworkURL) != nil, current == t { updateNowPlaying() } }
    }
}
