package com.driveplayer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lưu cục bộ: playlist, bài nghe gần đây, từ khóa tìm nhanh. */
interface LibraryStore {
    val playlist: StateFlow<List<Track>>
    val recents: StateFlow<List<Track>>
    val savedQueries: StateFlow<List<String>>
    fun addToPlaylist(track: Track)
    fun removeFromPlaylist(id: String)
    fun markPlayed(track: Track)
    fun addQuery(query: String)
    fun removeQuery(query: String)
}

class InMemoryLibraryStore(
    playlist: List<Track> = emptyList(),
    recents: List<Track> = emptyList(),
    queries: List<String> = emptyList(),
) : LibraryStore {
    override val playlist = MutableStateFlow(playlist)
    override val recents = MutableStateFlow(recents)
    override val savedQueries = MutableStateFlow(queries)

    override fun addToPlaylist(track: Track) {
        if (playlist.value.none { it.id == track.id }) playlist.value = playlist.value + track
    }
    override fun removeFromPlaylist(id: String) { playlist.value = playlist.value.filterNot { it.id == id } }
    override fun markPlayed(track: Track) {
        recents.value = (listOf(track) + recents.value.filterNot { it.id == track.id }).take(MAX_RECENTS)
    }
    override fun addQuery(query: String) {
        val q = query.trim()
        if (q.isNotEmpty() && q !in savedQueries.value) savedQueries.value = savedQueries.value + q
    }
    override fun removeQuery(query: String) { savedQueries.value = savedQueries.value - query }

    companion object { const val MAX_RECENTS = 50 }
}
