import Foundation

/// Một bài nghe được. `id` là video ID của YouTube.
struct Track: Codable, Hashable, Identifiable {
    let id: String
    let title: String
    let artist: String
    var artworkURL: URL?
}
