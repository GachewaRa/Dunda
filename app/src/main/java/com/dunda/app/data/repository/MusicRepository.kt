package com.dunda.app.data.repository

import android.content.Context
import com.dunda.app.data.local.AppDatabase
import com.dunda.app.data.local.MediaScanner
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class MusicRepository(context: Context) {

    private val mediaScanner = MediaScanner(context)
    private val database = AppDatabase.getInstance(context)
    private val playlistDao = database.playlistDao()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    suspend fun loadSongs() {
        withContext(Dispatchers.IO) {
            _songs.value = mediaScanner.scanMusic()
        }
    }

    fun getSongById(id: Long): Song? = _songs.value.find { it.id == id }

    fun getSongsByIds(ids: List<Long>): List<Song> {
        val songMap = _songs.value.associateBy { it.id }
        return ids.mapNotNull { songMap[it] }
    }

    fun getSongsSortedByBpm(): List<Song> {
        return _songs.value.sortedWith(
            compareBy<Song> { it.bpm == null }  // songs with BPM first
                .thenBy { it.bpm ?: Int.MAX_VALUE }
        )
    }

    // Playlist operations
    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist)
    }

    fun getPlaylistSongIds(playlistId: Long): Flow<List<Long>> {
        return playlistDao.getSongIdsForPlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        playlistDao.addSongToPlaylist(playlistId, songId)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }
}
