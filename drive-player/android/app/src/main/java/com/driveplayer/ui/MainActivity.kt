package com.driveplayer.ui

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.driveplayer.data.BrowseTree
import com.driveplayer.data.PrefsLibraryStore
import com.driveplayer.data.Track
import com.driveplayer.playback.PlaybackService
import com.driveplayer.source.YouTubeSource
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

/** Màn hình trên điện thoại: tìm bài, phát, thêm vào playlist, quản lý từ khóa "Tìm nhanh". */
@UnstableApi
class MainActivity : ComponentActivity() {

    private var controller by mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val store = PrefsLibraryStore.get(this)
        val source = YouTubeSource()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen(store, source) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({ controller = future.get() }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        controller?.release()
        controller = null
        super.onStop()
    }

    @Composable
    private fun Screen(store: PrefsLibraryStore, source: YouTubeSource) {
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<Track>>(emptyList()) }
        var lastQuery by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var tab by remember { mutableIntStateOf(0) }
        val playlist by store.playlist.collectAsState()
        val recents by store.recents.collectAsState()
        val queries by store.savedQueries.collectAsState()

        fun doSearch(q: String) {
            if (q.isBlank()) return
            tab = 0; loading = true; error = null
            scope.launch {
                runCatching { source.search(q) }
                    .onSuccess { results = it; lastQuery = q }
                    .onFailure { error = it.message ?: it.toString() }
                loading = false
            }
        }

        fun play(parent: String, list: List<Track>, index: Int) {
            val c = controller ?: return
            c.setMediaItems(list.map { PlaybackService.song(parent + BrowseTree.SEP + it.id, it) }, index, 0)
            c.prepare(); c.play()
        }

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Tìm trên YouTube") }, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { doSearch(query) }) { Text("Tìm") }
            }
            TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
                listOf("Kết quả", "Playlist", "Gần đây", "Tìm nhanh").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text("Lỗi: $it", color = MaterialTheme.colorScheme.error) }

            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TrackList(results, onPlay = { play(BrowseTree.QUERY_PREFIX + lastQuery, results, it) },
                        actionLabel = "+ Playlist", onAction = store::addToPlaylist)
                    1 -> TrackList(playlist, onPlay = { play(BrowseTree.PLAYLIST, playlist, it) },
                        actionLabel = "Xóa", onAction = { store.removeFromPlaylist(it.id) })
                    2 -> TrackList(recents, onPlay = { play(BrowseTree.RECENTS, recents, it) },
                        actionLabel = "+ Playlist", onAction = store::addToPlaylist)
                    3 -> QueryList(queries, current = query, onAdd = store::addQuery,
                        onRemove = store::removeQuery, onOpen = { query = it; doSearch(it) })
                }
            }
            NowPlayingBar()
        }
    }

    @Composable
    private fun TrackList(tracks: List<Track>, onPlay: (Int) -> Unit, actionLabel: String, onAction: (Track) -> Unit) {
        if (tracks.isEmpty()) { Text("Trống", Modifier.padding(16.dp)); return }
        LazyColumn {
            items(tracks.size) { i ->
                val t = tracks[i]
                ListItem(
                    headlineContent = { Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(t.artist + if (t.durationSec > 0) " · ${t.durationSec / 60}:%02d".format(t.durationSec % 60) else "") },
                    trailingContent = { TextButton(onClick = { onAction(t) }) { Text(actionLabel) } },
                    modifier = Modifier.clickable { onPlay(i) },
                )
            }
        }
    }

    @Composable
    private fun QueryList(queries: List<String>, current: String, onAdd: (String) -> Unit, onRemove: (String) -> Unit, onOpen: (String) -> Unit) {
        Column {
            Text(
                "CarPlay/Android Auto hiện các từ khóa này trong mục \"Tìm nhanh\" để chọn bài khi đang lái.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp),
            )
            TextButton(onClick = { onAdd(current) }, enabled = current.isNotBlank()) {
                Text("+ Lưu \"$current\" thành từ khóa")
            }
            LazyColumn {
                items(queries) { q ->
                    ListItem(
                        headlineContent = { Text(q) },
                        trailingContent = { TextButton(onClick = { onRemove(q) }) { Text("Xóa") } },
                        modifier = Modifier.clickable { onOpen(q) },
                    )
                }
            }
        }
    }

    @Composable
    private fun NowPlayingBar() {
        val c = controller ?: return
        var title by remember { mutableStateOf(c.mediaMetadata.title?.toString().orEmpty()) }
        var playing by remember { mutableStateOf(c.isPlaying) }
        DisposableEffect(c) {
            val l = object : androidx.media3.common.Player.Listener {
                override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                    title = player.mediaMetadata.title?.toString().orEmpty(); playing = player.isPlaying
                }
            }
            c.addListener(l)
            onDispose { c.removeListener(l) }
        }
        if (title.isEmpty()) return
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { c.seekToPreviousMediaItem() }) { Text("⏮") }
            TextButton(onClick = { if (playing) c.pause() else c.play() }) { Text(if (playing) "⏸" else "▶") }
            TextButton(onClick = { c.seekToNextMediaItem() }) { Text("⏭") }
        }
    }
}
