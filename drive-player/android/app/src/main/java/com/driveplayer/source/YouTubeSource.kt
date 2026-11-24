package com.driveplayer.source

import com.driveplayer.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Tìm và lấy stream audio YouTube qua NewPipeExtractor.
 * CHỈ DÙNG CÁ NHÂN: cách này không tuân thủ điều khoản YouTube.
 * Nếu YouTube đổi cấu trúc mà app hỏng, hãy nâng version NewPipeExtractor trong libs.versions.toml.
 */
class YouTubeSource : AudioSource {

    private val service = ServiceList.YouTube
    private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    init { ensureInit() }

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toTrack() }
    }

    override fun resolveStreamUrl(trackId: String): String {
        urlCache[trackId]?.let { (url, at) ->
            if (System.currentTimeMillis() - at < CACHE_MS) return url
        }
        val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$trackId")
        val best = info.audioStreams
            .filter { it.isUrl && it.content.isNotEmpty() }
            .maxByOrNull { it.averageBitrate }
            ?: error("Không tìm thấy stream audio cho $trackId")
        urlCache[trackId] = best.content to System.currentTimeMillis()
        return best.content
    }

    private fun StreamInfoItem.toTrack(): Track? {
        val id = Regex("[?&]v=([\\w-]{11})").find(url)?.groupValues?.get(1) ?: return null
        return Track(
            id = id,
            title = name,
            artist = uploaderName.orEmpty(),
            artworkUrl = thumbnails.maxByOrNull { it.width }?.url,
            durationSec = duration.coerceAtLeast(0),
        )
    }

    companion object {
        private const val CACHE_MS = 3 * 60 * 60 * 1000L // link YouTube hết hạn sau ~6h
        @Volatile private var initialized = false

        private fun ensureInit() {
            if (initialized) return
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(OkHttpDownloader(), Localization("vi", "VN"))
                    initialized = true
                }
            }
        }
    }
}

/** Downloader mà NewPipeExtractor cần, dựa trên OkHttp. */
private class OkHttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha", request.url())
            return Response(
                resp.code, resp.message, resp.headers.toMultimap(),
                resp.body?.string(), resp.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
