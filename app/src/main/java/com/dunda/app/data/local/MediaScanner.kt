package com.dunda.app.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.dunda.app.data.model.Song

class MediaScanner(private val context: Context) {

    companion object {
        /**
         * Filename/path signatures of audio that isn't music: WhatsApp voice
         * notes (PTT-/AUD-...-WA...), call & screen recordings, Messenger
         * clips, browser "videoplayback" dumps, VLC captures, and long
         * hex-named blobs. Checked against title AND file path.
         */
        private val NON_MUSIC_PATTERNS = listOf(
            Regex("""(^|/)(aud|ptt)-\d{8}-wa\d+""", RegexOption.IGNORE_CASE),
            Regex("""/whatsapp[ /]""", RegexOption.IGNORE_CASE),
            Regex("""whatsapp (audio|ptt|voice)""", RegexOption.IGNORE_CASE),
            Regex("""(^|/)(call[ _-]?record|recordings?|voice[ _-]?(note|record|memo)|sound[ _-]?record)""", RegexOption.IGNORE_CASE),
            Regex("""screen[ _-]?record""", RegexOption.IGNORE_CASE),
            Regex("""(^|/)videoplayback""", RegexOption.IGNORE_CASE),
            Regex("""(^|/)vlc-record""", RegexOption.IGNORE_CASE),
            Regex("""messenger creation""", RegexOption.IGNORE_CASE),
            Regex("""(^|/)[0-9a-f]{20,}( |\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""(^|/)rec[ _-]?\d{3,}""", RegexOption.IGNORE_CASE),
        )

        /**
         * Judged on the file PATH only, never the embedded title: downloads
         * often get renamed to a proper song name while the title tag still
         * says "videoplayback (1)" — the human-chosen filename wins. (Found
         * the hard way: a renamed Davido m4a was filtered for its stale tag.)
         */
        fun looksLikeNonMusic(title: String, path: String): Boolean =
            NON_MUSIC_PATTERNS.any { it.containsMatchIn(path) }
    }

    fun scanMusic(minDurationMs: Long = 0L, excludeNonMusic: Boolean = true): List<Song> {
        val songs = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
        )

        val minDuration = maxOf(5000L, minDurationMs)
        // MediaStore's is_music flag misses some genuine music (notoriously
        // m4a from certain encoders), so known music extensions are accepted
        // regardless of the flag — our own duration + non-music-pattern
        // filters are the real gatekeepers. Ringtones/alarms stay out.
        val musicExtensions = listOf("m4a", "flac", "opus", "ogg", "wav", "aac")
            .joinToString(" OR ") { "${MediaStore.Audio.Media.DATA} LIKE '%.$it'" }
        val selection =
            "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ($musicExtensions)) " +
                "AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 " +
                "AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0 " +
                "AND ${MediaStore.Audio.Media.IS_ALARM} = 0 " +
                "AND ${MediaStore.Audio.Media.DURATION} >= $minDuration"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)

                if (excludeNonMusic) {
                    val title = cursor.getString(titleColumn) ?: ""
                    val path = cursor.getString(dataColumn) ?: ""
                    if (looksLikeNonMusic(title, path)) continue
                }

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                )

                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleColumn) ?: "Unknown",
                        artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                        album = cursor.getString(albumColumn) ?: "Unknown Album",
                        duration = cursor.getLong(durationColumn),
                        uri = contentUri,
                        albumArtUri = albumArtUri,
                        dateAdded = cursor.getLong(dateAddedColumn)
                    )
                )
            }
        }

        return songs
    }
}
