package com.driveplayer.data

/**
 * Cây duyệt nhạc hiển thị trên Android Auto (không phụ thuộc Android, nên unit test được).
 *
 * root
 *  ├─ playlist        → các bài trong playlist
 *  ├─ recents         → bài nghe gần đây
 *  └─ queries         → từ khóa tìm nhanh
 *       └─ q:<từ khóa> → kết quả tìm kiếm
 */
class BrowseTree(
    private val store: LibraryStore,
    private val search: suspend (String) -> List<Track>,
) {
    sealed interface Node {
        val id: String
        data class Folder(override val id: String, val title: String) : Node
        data class Song(override val id: String, val track: Track) : Node
    }

    private val searchCache = mutableMapOf<String, List<Track>>()
    private val knownTracks = mutableMapOf<String, Track>()

    val rootFolders = listOf(
        Node.Folder(PLAYLIST, "Playlist"),
        Node.Folder(RECENTS, "Gần đây"),
        Node.Folder(QUERIES, "Tìm nhanh"),
    )

    suspend fun children(parentId: String): List<Node> = when {
        parentId == ROOT -> rootFolders
        parentId == PLAYLIST -> songs(parentId, store.playlist.value)
        parentId == RECENTS -> songs(parentId, store.recents.value)
        parentId == QUERIES -> store.savedQueries.value.map { Node.Folder(QUERY_PREFIX + it, it) }
        parentId.startsWith(QUERY_PREFIX) -> songs(parentId, searchCached(parentId.removePrefix(QUERY_PREFIX)))
        else -> emptyList()
    }

    suspend fun searchCached(query: String): List<Track> =
        searchCache[query] ?: search(query).also { searchCache[query] = it }

    /** Tìm kiếm từ Android Auto (bàn phím / giọng nói). Kết quả nằm trong thư mục ảo q:<query>. */
    suspend fun searchSongs(query: String): List<Node.Song> =
        songs(QUERY_PREFIX + query, searchCached(query))

    /** Lấy thông tin bài từ mediaId dạng "<parentId>|<videoId>". */
    fun track(mediaId: String): Track? = knownTracks[mediaId] ?: parseVideoId(mediaId)?.let { vid ->
        (store.playlist.value + store.recents.value).firstOrNull { it.id == vid }
    }

    /** Danh sách anh em cùng thư mục — để next/previous hoạt động trong hàng chờ. */
    suspend fun siblings(mediaId: String): List<Node.Song> {
        val parent = mediaId.substringBefore(SEP, missingDelimiterValue = "")
        if (parent.isEmpty()) return emptyList()
        return children(parent).filterIsInstance<Node.Song>()
    }

    private fun songs(parentId: String, tracks: List<Track>) = tracks.map {
        val id = parentId + SEP + it.id
        knownTracks[id] = it
        Node.Song(id, it)
    }

    companion object {
        const val ROOT = "root"
        const val PLAYLIST = "playlist"
        const val RECENTS = "recents"
        const val QUERIES = "queries"
        const val QUERY_PREFIX = "q:"
        const val SEP = "|"

        /** "playlist|dQw4w9WgXcQ" → "dQw4w9WgXcQ"; một video ID trần cũng được chấp nhận. */
        fun parseVideoId(mediaId: String): String? = when {
            SEP in mediaId -> mediaId.substringAfterLast(SEP).ifEmpty { null }
            mediaId.length == 11 -> mediaId
            else -> null
        }
    }
}
