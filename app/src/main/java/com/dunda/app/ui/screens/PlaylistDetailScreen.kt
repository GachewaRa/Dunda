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

/** Playlist id for the built-in Favourites virtual playlist. */
const val FAVOURITES_PLAYLIST_ID = -1L

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
    onBack: () -> Unit
) {
    val isFavourites = playlistId == FAVOURITES_PLAYLIST_ID

    val playlists by musicViewModel.playlists.collectAsState()
    val playlist = playlists.find { it.id == playlistId }
    val songIds by musicViewModel.getPlaylistSongIds(playlistId)
        .collectAsState(initial = emptyList())
    val allSongs by musicViewModel.songs.collectAsState()
    val favourites by musicViewModel.favourites.collectAsState()
    val playCounts by musicViewModel.playCounts.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()

    // Favourites has no manual positions, so it defaults to title order.
    var favouritesSort by rememberSaveable { mutableStateOf(SortMode.TITLE_ASC) }
    val sortMode = if (isFavourites) favouritesSort
                   else SortMode.fromName(playlist?.sortMode)

    val baseSongs = if (isFavourites) favourites
                    else remember(songIds, allSongs) { musicViewModel.getSongsByIds(songIds) }

    // Playback follows the displayed order (docs/FEATURES.md §7).
    val displayedSongs = remember(baseSongs, sortMode, playCounts) {
        musicViewModel.sortSongs(baseSongs, sortMode)
    }

    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        if (isFavourites) "Favourites" else playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.titleLarge
                    )
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
                        .filter { it != SortMode.BPM && (!isFavourites || it != SortMode.CUSTOM) }
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
                                    if (isFavourites) favouritesSort = mode
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
                    if (isFavourites) {
                        "No favourites yet.\nTap the heart on any song to add it."
                    } else {
                        "No songs in this playlist.\nAdd songs from the home screen."
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
                        },
                        onToggleFavourite = { musicViewModel.toggleFavourite(song) }
                    )
                }
            }
        }
    }
}
