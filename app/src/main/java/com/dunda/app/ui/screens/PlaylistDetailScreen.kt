package com.dunda.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dunda.app.data.model.SortMode
import com.dunda.app.ui.components.SongItem
import com.dunda.app.viewmodel.MusicViewModel
import com.dunda.app.viewmodel.PlayerViewModel

/** IDs for the built-in virtual playlists (never collide with Room's ids > 0). */
const val FAVOURITES_PLAYLIST_ID = -1L
const val RECENTLY_ADDED_PLAYLIST_ID = -2L
const val MOST_PLAYED_PLAYLIST_ID = -3L

fun sortModeLabel(mode: SortMode): String = when (mode) {
    SortMode.CUSTOM -> "Manual order"
    SortMode.TITLE_ASC -> "Title A–Z"
    SortMode.TITLE_DESC -> "Title Z–A"
    SortMode.ARTIST_ASC -> "Artist"
    SortMode.DATE_ADDED_DESC -> "Recently added"
    SortMode.DATE_ADDED_ASC -> "Oldest added"
    SortMode.PLAY_COUNT_DESC -> "Most played"
    SortMode.PLAY_COUNT_ASC -> "Least played"
    SortMode.DURATION_ASC -> "Shortest first"
    SortMode.DURATION_DESC -> "Longest first"
    SortMode.BPM -> "BPM"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    musicViewModel: MusicViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit = {}
) {
    val isVirtual = playlistId < 0

    val playlists by musicViewModel.playlists.collectAsState()
    val playlist = playlists.find { it.id == playlistId }
    val songIds by musicViewModel.getPlaylistSongIds(playlistId)
        .collectAsState(initial = emptyList())
    val allSongs by musicViewModel.songs.collectAsState()
    val favourites by musicViewModel.favourites.collectAsState()
    val playCounts by musicViewModel.playCounts.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()

    val title = when (playlistId) {
        FAVOURITES_PLAYLIST_ID -> "Favourites"
        RECENTLY_ADDED_PLAYLIST_ID -> "Recently Added"
        MOST_PLAYED_PLAYLIST_ID -> "Most Played"
        else -> playlist?.name ?: "Playlist"
    }

    // Virtual playlists have no manual positions; each gets a natural default
    // order, overridable per visit (not persisted).
    val defaultVirtualSort = when (playlistId) {
        RECENTLY_ADDED_PLAYLIST_ID -> SortMode.DATE_ADDED_DESC
        MOST_PLAYED_PLAYLIST_ID -> SortMode.PLAY_COUNT_DESC
        else -> SortMode.TITLE_ASC
    }
    var virtualSort by rememberSaveable { mutableStateOf<SortMode?>(null) }
    val sortMode = if (isVirtual) virtualSort ?: defaultVirtualSort
                   else SortMode.fromName(playlist?.sortMode)

    val baseSongs = when (playlistId) {
        FAVOURITES_PLAYLIST_ID -> favourites
        RECENTLY_ADDED_PLAYLIST_ID -> allSongs
        MOST_PLAYED_PLAYLIST_ID -> remember(allSongs, playCounts) {
            allSongs.filter { (playCounts[it.id] ?: 0) > 0 }
        }
        else -> remember(songIds, allSongs) { musicViewModel.getSongsByIds(songIds) }
    }

    // Playback follows the displayed order (docs/FEATURES.md §7).
    val displayedSongs = remember(baseSongs, sortMode, playCounts) {
        musicViewModel.sortSongs(baseSongs, sortMode)
    }

    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${displayedSongs.size} songs • ${sortModeLabel(sortMode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Sort")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortMode.entries
                        .filter { it != SortMode.BPM && (!isVirtual || it != SortMode.CUSTOM) }
                        .forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        sortModeLabel(mode),
                                        color = if (mode == sortMode) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    if (isVirtual) virtualSort = mode
                                    else musicViewModel.setPlaylistSortMode(playlistId, mode)
                                    showSortMenu = false
                                }
                            )
                        }
                }

                if (displayedSongs.isNotEmpty()) {
                    // True-shuffle play: random start, no repeats until exhausted
                    TextButton(onClick = { playerViewModel.playShuffled(displayedSongs) }) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(" Shuffle", color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (displayedSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (playlistId) {
                        FAVOURITES_PLAYLIST_ID ->
                            "No favourites yet.\nTap the heart on any song to add it."
                        MOST_PLAYED_PLAYLIST_ID ->
                            "No plays logged yet.\nThis playlist fills up as you listen."
                        else -> "No songs in this playlist.\nAdd songs from the home screen."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(displayedSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
                        isCurrentSong = song.id == currentSong?.id,
                        playCount = playCounts[song.id] ?: 0,
                        onClick = {
                            playerViewModel.playSong(song, displayedSongs)
                            onOpenNowPlaying()
                        },
                        // Only real playlists have removable membership
                        onRemoveFromPlaylist = if (!isVirtual) {
                            { musicViewModel.removeSongFromPlaylist(playlistId, song.id) }
                        } else null,
                        onToggleFavourite = { musicViewModel.toggleFavourite(song) }
                    )
                }
            }
        }
    }
}
