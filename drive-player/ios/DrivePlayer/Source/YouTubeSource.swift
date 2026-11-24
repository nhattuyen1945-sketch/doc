import Foundation
import YouTubeKit

/// Tìm kiếm qua YouTube Data API v3 (cần API key miễn phí, khoảng 100 lần tìm/ngày).
/// Lấy stream audio qua YouTubeKit. CHỈ DÙNG CÁ NHÂN: phần phát ngầm không tuân thủ điều khoản YouTube.
/// Nếu YouTube đổi cấu trúc mà app hỏng: File > Packages > Update to Latest Package Versions.
struct YouTubeSource: AudioSource {
    let apiKey: () -> String

    func search(_ query: String) async throws -> [Track] {
        let key = apiKey()
        guard !key.isEmpty else { throw SourceError.missingAPIKey }
        var c = URLComponents(string: "https://www.googleapis.com/youtube/v3/search")!
        c.queryItems = [
            .init(name: "part", value: "snippet"), .init(name: "type", value: "video"),
            .init(name: "maxResults", value: "25"), .init(name: "regionCode", value: "VN"),
            .init(name: "q", value: query), .init(name: "key", value: key),
        ]
        let (data, resp) = try await URLSession.shared.data(from: c.url!)
        if let http = resp as? HTTPURLResponse, http.statusCode != 200 {
            throw SourceError.http(http.statusCode, String(decoding: data, as: UTF8.self))
        }
        return try JSONDecoder().decode(SearchResponse.self, from: data).items.compactMap { item in
            guard let id = item.id.videoId else { return nil }
            let thumbs = item.snippet.thumbnails
            return Track(
                id: id,
                title: item.snippet.title.htmlDecoded,
                artist: item.snippet.channelTitle.htmlDecoded,
                artworkURL: (thumbs.high ?? thumbs.medium ?? thumbs.default)?.url
            )
        }
    }

    func streamURL(for trackID: String) async throws -> URL {
        let streams = try await YouTube(videoID: trackID).streams
        // AVPlayer phát được m4a (AAC), không phát được webm/opus.
        guard let best = streams.filterAudioOnly()
            .filter({ $0.fileExtension == .m4a })
            .highestAudioBitrateStream()
        else { throw SourceError.noAudioStream }
        return best.url
    }
}

private struct SearchResponse: Decodable {
    struct Item: Decodable {
        struct ID: Decodable { let videoId: String? }
        struct Snippet: Decodable {
            struct Thumbs: Decodable {
                struct T: Decodable { let url: URL }
                let `default`: T?, medium: T?, high: T?
            }
            let title: String, channelTitle: String, thumbnails: Thumbs
        }
        let id: ID, snippet: Snippet
    }
    let items: [Item]
}

private extension String {
    /// Data API trả tiêu đề có &amp; &#39; &quot;...
    var htmlDecoded: String {
        var s = self
        for (k, v) in [("&#39;", "'"), ("&quot;", "\""), ("&lt;", "<"), ("&gt;", ">"), ("&amp;", "&")] { // &amp; cuối cùng
            s = s.replacingOccurrences(of: k, with: v)
        }
        return s
    }
}
