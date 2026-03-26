package com.dunda.app.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,       // milliseconds
    val uri: Uri,
    val albumArtUri: Uri?,
    val bpm: Int? = null,     // beats per minute (null = not yet analyzed)
    val dateAdded: Long = 0
) {
    val durationFormatted: String
        get() {
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
