package com.driveplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lưu vào SharedPreferences dạng JSON — đủ cho app cá nhân. */
class PrefsLibraryStore private constructor(
    private val context: Context,
    private val mem: InMemoryLibraryStore,
) : LibraryStore by mem {

    private val prefs get() = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    override fun addToPlaylist(track: Track) { mem.addToPlaylist(track); save() }
    override fun removeFromPlaylist(id: String) { mem.removeFromPlaylist(id); save() }
    override fun markPlayed(track: Track) { mem.markPlayed(track); save() }
    override fun addQuery(query: String) { mem.addQuery(query); save() }
    override fun removeQuery(query: String) { mem.removeQuery(query); save() }

    private fun save() {
        prefs.edit()
            .putString(KEY_PLAYLIST, mem.playlist.value.toJson())
            .putString(KEY_RECENTS, mem.recents.value.toJson())
            .putString(KEY_QUERIES, JSONArray(mem.savedQueries.value).toString())
            .apply()
    }

    companion object {
        private const val KEY_PLAYLIST = "playlist"
        private const val KEY_RECENTS = "recents"
        private const val KEY_QUERIES = "queries"
        @Volatile private var instance: PrefsLibraryStore? = null

        fun get(context: Context): PrefsLibraryStore = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val p = app.getSharedPreferences("library", Context.MODE_PRIVATE)
                val queries = JSONArray(p.getString(KEY_QUERIES, null) ?: DEFAULT_QUERIES)
                val mem = InMemoryLibraryStore(
                    playlist = p.getString(KEY_PLAYLIST, null).toTracks(),
                    recents = p.getString(KEY_RECENTS, null).toTracks(),
                    queries = List(queries.length()) { queries.getString(it) },
                )
                PrefsLibraryStore(app, mem).also { instance = it }
            }
        }

        private const val DEFAULT_QUERIES = """["nhạc trẻ hot","lofi chill","bolero"]"""

        private fun List<Track>.toJson(): String = JSONArray(map {
            JSONObject()
                .put("id", it.id).put("title", it.title).put("artist", it.artist)
                .put("art", it.artworkUrl).put("dur", it.durationSec)
        }).toString()

        private fun String?.toTracks(): List<Track> {
            if (this == null) return emptyList()
            val arr = JSONArray(this)
            return List(arr.length()) {
                val o = arr.getJSONObject(it)
                Track(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    artist = o.optString("artist"),
                    artworkUrl = o.optString("art").takeIf { s -> s.isNotEmpty() && s != "null" },
                    durationSec = o.optLong("dur"),
                )
            }
        }
    }
}
