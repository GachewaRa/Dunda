package com.dunda.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dunda.app.data.local.SongPlayStats
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.Song
import com.dunda.app.data.model.SortMode
import com.dunda.app.data.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val repository = MusicRepository(application)

    val songs: StateFlow<List<Song>> = repository.songs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favourites: StateFlow<List<Song>> = repository.favourites
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** All-time play counts, used for play-count sorting of any list. */
    val playCounts: StateFlow<Map<Long, Int>> = repository.allTimePlayCounts
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _sortMode = MutableStateFlow(SortMode.TITLE_ASC)
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val playlists = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadSongs()
        viewModelScope.launch {
            repository.settings.librarySortMode.collect { saved ->
                _sortMode.value = if (saved == SortMode.CUSTOM) SortMode.TITLE_ASC else saved
            }
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.refreshLibrary()
            _isLoading.value = false
        }
    }

    /** Sort [list] by [mode]; CUSTOM keeps the incoming order (manual positions). */
    fun sortSongs(list: List<Song>, mode: SortMode): List<Song> {
        val counts = playCounts.value
        return when (mode) {
            SortMode.CUSTOM -> list
            SortMode.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortMode.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortMode.ARTIST_ASC -> list.sortedBy { it.artist.lowercase() }
            SortMode.DATE_ADDED_DESC -> list.sortedByDescending { it.dateAdded }
            SortMode.DATE_ADDED_ASC -> list.sortedBy { it.dateAdded }
            SortMode.PLAY_COUNT_DESC -> list.sortedByDescending { counts[it.id] ?: 0 }
            SortMode.PLAY_COUNT_ASC -> list.sortedBy { counts[it.id] ?: 0 }
            SortMode.DURATION_ASC -> list.sortedBy { it.duration }
            SortMode.DURATION_DESC -> list.sortedByDescending { it.duration }
            SortMode.BPM -> list.sortedWith(
                compareBy<Song> { it.bpm == null }.thenBy { it.bpm ?: Int.MAX_VALUE }
            )
        }
    }

    fun getSortedSongs(): List<Song> = sortSongs(songs.value, _sortMode.value)

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        viewModelScope.launch { repository.settings.setLibrarySortMode(mode) }
    }

    fun getSongsByIds(ids: List<Long>): List<Song> {
        val songMap = songs.value.associateBy { it.id }
        return ids.mapNotNull { songMap[it] }
    }

    fun toggleFavourite(song: Song) {
        viewModelScope.launch { repository.setFavourite(song.id, !song.isFavourite) }
    }

    // Statistics (docs/FEATURES.md §6)
    fun statsInRange(from: Long, to: Long): Flow<List<SongPlayStats>> =
        repository.statsInRange(from, to)

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

    fun setPlaylistSortMode(playlistId: Long, mode: SortMode) {
        viewModelScope.launch { repository.setPlaylistSortMode(playlistId, mode) }
    }

    fun getPlaylistSongIds(playlistId: Long) = repository.getPlaylistSongIds(playlistId)
}
