package com.dunda.app.data.repository

import android.content.Context
import com.dunda.app.data.local.AppDatabase
import com.dunda.app.data.local.MediaScanner
import com.dunda.app.data.local.SettingsStore
import com.dunda.app.data.local.SongPlayStats
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.Song
import com.dunda.app.data.model.SortMode
import com.dunda.app.data.model.toEntity
import com.dunda.app.data.model.toSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepository(context: Context) {

    private val mediaScanner = MediaScanner(context)
    private val database = AppDatabase.getInstance(context)
    private val playlistDao = database.playlistDao()
    private val songDao = database.songDao()
    private val playEventDao = database.playEventDao()
    val settings = SettingsStore(context)

    /**
     * The library, served from the Room cache (docs/FEATURES.md §3) — the
     * single source of truth for song lists. MediaStore is ingest-only via
     * refreshLibrary().
     */
    val songs: Flow<List<Song>> =
        songDao.getAllPresent().map { list -> list.map { it.toSong() } }

    val favourites: Flow<List<Song>> =
        songDao.getFavourites().map { list -> list.map { it.toSong() } }

    /** Scan MediaStore and sync into the cache, preserving per-song user data. */
    suspend fun refreshLibrary() {
        withContext(Dispatchers.IO) {
            songDao.sync(mediaScanner.scanMusic().map { it.toEntity() })
        }
    }

    suspend fun setFavourite(songId: Long, favourite: Boolean) {
        songDao.setFavourite(songId, favourite)
    }

    // Play statistics (docs/FEATURES.md §6)

    fun statsInRange(from: Long, to: Long): Flow<List<SongPlayStats>> =
        playEventDao.statsInRange(from, to)

    /** All-time play count per song id (songs with zero plays absent). */
    val allTimePlayCounts: Flow<Map<Long, Int>> =
        playEventDao.playCountsInRange(0, Long.MAX_VALUE)
            .map { rows -> rows.associate { it.songId to it.playCount } }

    // Playlist operations
    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    /** Song count per playlist id (playlists with no songs absent). */
    val playlistSongCounts: Flow<Map<Long, Int>> =
        playlistDao.getPlaylistSongCounts()
            .map { rows -> rows.associate { it.playlistId to it.songCount } }

    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylistWithSongs(playlist)
    }

    fun getPlaylistSongIds(playlistId: Long): Flow<List<Long>> {
        return playlistDao.getSongIdsForPlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        playlistDao.addSongToPlaylist(playlistId, songId)
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        playlistDao.addSongsToPlaylist(playlistId, songIds)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun setPlaylistSortMode(playlistId: Long, mode: SortMode) {
        playlistDao.setSortMode(playlistId, mode.name)
    }
}
