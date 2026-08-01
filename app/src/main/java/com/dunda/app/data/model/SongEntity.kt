package com.dunda.app.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-side cache of the device music library, synced from MediaStore by
 * MediaScanner. Rows are never deleted when a song disappears from the device;
 * they are marked isPresent = false so favourite status and play history
 * survive SD card remounts and rescans.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,          // MediaStore ID
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,                // milliseconds
    val uri: String,
    val albumArtUri: String?,
    val dateAdded: Long,
    val bpm: Int? = null,
    val isFavourite: Boolean = false,
    val isPresent: Boolean = true,
)

fun SongEntity.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    uri = Uri.parse(uri),
    albumArtUri = albumArtUri?.let(Uri::parse),
    bpm = bpm,
    dateAdded = dateAdded,
    isFavourite = isFavourite,
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    uri = uri.toString(),
    albumArtUri = albumArtUri?.toString(),
    dateAdded = dateAdded,
    bpm = bpm,
    isFavourite = isFavourite,
)
