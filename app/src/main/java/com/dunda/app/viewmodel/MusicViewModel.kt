package com.dunda.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.Song
import com.dunda.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode {
    TITLE, ARTIST, DATE_ADDED, BPM
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository(application)

    val songs: StateFlow<List<Song>> = repository.songs

    private val _sortMode = MutableStateFlow(SortMode.TITLE)
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val playlists = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadSongs()
    }

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.loadSongs()
            _isLoading.value = false
        }
    }

    fun getSortedSongs(): List<Song> {
        val songList = songs.value
        return when (_sortMode.value) {
            SortMode.TITLE -> songList.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> songList.sortedBy { it.artist.lowercase() }
            SortMode.DATE_ADDED -> songList.sortedByDescending { it.dateAdded }
            SortMode.BPM -> songList.sortedWith(
                compareBy<Song> { it.bpm == null }
                    .thenBy { it.bpm ?: Int.MAX_VALUE }
            )
        }
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun getPlaylistSongIds(playlistId: Long) = repository.getPlaylistSongIds(playlistId)
}
