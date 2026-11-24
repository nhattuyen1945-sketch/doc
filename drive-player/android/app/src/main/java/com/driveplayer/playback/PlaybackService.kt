package com.driveplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.driveplayer.data.BrowseTree
import com.driveplayer.data.PrefsLibraryStore
import com.driveplayer.data.Track
import com.driveplayer.source.AudioSource
import com.driveplayer.source.YouTubeSource
import com.driveplayer.ui.MainActivity
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * Service phát nhạc. Android Auto kết nối vào đây và tự dựng giao diện xe từ [BrowseTree].
 * Nhạc vẫn phát khi bạn chuyển sang Google Maps / VietMap (chế độ chia màn hình).
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var source: AudioSource
    private lateinit var tree: BrowseTree
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val store = PrefsLibraryStore.get(this)
        source = YouTubeSource()
        tree = BrowseTree(store) { source.search(it) }

        // URI dạng "yt:<videoId>" được đổi sang link stream thật ngay lúc tải (trên thread nền),
        // nên link không bị hết hạn khi bài nằm lâu trong hàng chờ.
        val http = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
        val resolving = ResolvingDataSource.Factory(http) { spec ->
            if (spec.uri.scheme == SCHEME) spec.withUri(Uri.parse(source.resolveStreamUrl(spec.uri.schemeSpecificPart)))
            else spec
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true, // tự giảm/tạm dừng khi Maps đọc chỉ đường
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                item?.let { tree.track(it.mediaId) }?.let(store::markPlayed)
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Bài lỗi (video bị chặn, hết hạn...) → bỏ qua sang bài tiếp.
                if (player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare(); player.play() }
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaLibrarySession.Builder(this, player, Callback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class Callback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            immediate(LibraryResult.ofItem(folder(BrowseTree.ROOT, "Drive Player"), params))

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val items = tree.children(parentId).map { it.toMediaItem() }
            LibraryResult.ofItemList(items.page(page, pageSize), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val track = tree.track(mediaId)
                ?: return immediate(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            return immediate(LibraryResult.ofItem(song(mediaId, track), null))
        }

        override fun onSearch(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            val count = runCatching { tree.searchSongs(query).size }.getOrDefault(0)
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val items = tree.searchSongs(query).map { it.toMediaItem() }
            LibraryResult.ofItemList(items.page(page, pageSize), params)
        }

        /**
         * Khi người dùng bấm 1 bài trên xe: đưa cả thư mục chứa nó vào hàng chờ (để next/prev chạy),
         * bắt đầu từ bài được chọn. Lệnh giọng nói "Play X on Drive Player" đi qua searchQuery.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val voiceQuery = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
            val single = mediaItems.singleOrNull()?.mediaId
            when {
                !voiceQuery.isNullOrBlank() -> {
                    val songs = tree.searchSongs(voiceQuery)
                    MediaSession.MediaItemsWithStartPosition(songs.map { it.toPlayable() }, 0, C.TIME_UNSET)
                }
                single != null && tree.siblings(single).isNotEmpty() -> {
                    val sibs = tree.siblings(single)
                    val idx = sibs.indexOfFirst { it.id == single }.coerceAtLeast(0)
                    MediaSession.MediaItemsWithStartPosition(sibs.map { it.toPlayable() }, idx, startPositionMs)
                }
                else -> MediaSession.MediaItemsWithStartPosition(
                    mediaItems.map { resolve(it) }, startIndex, startPositionMs,
                )
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = immediate(mediaItems.map { resolve(it) }.toMutableList())

        private fun resolve(item: MediaItem): MediaItem {
            if (item.localConfiguration != null) return item
            val track = tree.track(item.mediaId)
            val vid = BrowseTree.parseVideoId(item.mediaId) ?: return item
            return song(item.mediaId, track ?: Track(vid, item.mediaMetadata.title?.toString() ?: vid, ""))
        }
    }

    private fun BrowseTree.Node.toMediaItem(): MediaItem = when (this) {
        is BrowseTree.Node.Folder -> folder(id, title)
        is BrowseTree.Node.Song -> song(id, track)
    }

    private fun BrowseTree.Node.Song.toPlayable() = song(id, track)

    companion object {
        const val SCHEME = "yt"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build(),
            ).build()

        fun song(mediaId: String, t: Track): MediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri("$SCHEME:${t.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title).setArtist(t.artist)
                    .setArtworkUri(t.artworkUrl?.let(Uri::parse))
                    .setIsBrowsable(false).setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(Bundle().apply { putString(EXTRA_VIDEO_ID, t.id) })
                    .build(),
            ).build()

        const val EXTRA_VIDEO_ID = "video_id"

        private fun <T> immediate(v: T): ListenableFuture<T> =
            com.google.common.util.concurrent.Futures.immediateFuture(v)

        private fun List<MediaItem>.page(page: Int, size: Int): List<MediaItem> {
            if (size <= 0 || size == Int.MAX_VALUE) return this
            val from = (page * size).coerceAtMost(this.size)
            return subList(from, (from + size).coerceAtMost(this.size))
        }
    }
}
