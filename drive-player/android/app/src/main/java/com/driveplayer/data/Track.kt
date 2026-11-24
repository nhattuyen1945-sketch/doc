package com.driveplayer.data

/** Một bài nghe được. [id] là video ID của YouTube. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationSec: Long = 0,
)
