import Foundation

/// Nguồn nhạc. Muốn đổi nguồn (file trên máy, link MP3, ...) chỉ cần implement protocol này.
protocol AudioSource {
    func search(_ query: String) async throws -> [Track]
    func streamURL(for trackID: String) async throws -> URL
}

enum SourceError: LocalizedError {
    case missingAPIKey, noAudioStream, http(Int, String)
    var errorDescription: String? {
        switch self {
        case .missingAPIKey: return "Chưa nhập YouTube API key (mục Cài đặt)."
        case .noAudioStream: return "Không tìm thấy stream audio phát được."
        case .http(let code, let body): return "HTTP \(code): \(body.prefix(200))"
        }
    }
}
