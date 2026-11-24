package com.driveplayer.source

import com.driveplayer.data.Track

/** Nguồn nhạc. Đổi nguồn (YouTube, file trên máy, ...) chỉ cần implement interface này. */
interface AudioSource {
    suspend fun search(query: String): List<Track>
    /** Trả về URL stream audio phát trực tiếp được. Hàm chặn — gọi trên thread nền. */
    fun resolveStreamUrl(trackId: String): String
}
