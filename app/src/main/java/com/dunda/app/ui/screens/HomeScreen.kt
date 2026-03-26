package com.dunda.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.Song
import com.dunda.app.ui.components.SongItem
import com.dunda.app.viewmodel.MusicViewModel
import com.dunda.app.viewmodel.PlayerViewModel
import com.dunda.app.viewmodel.SortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel,
    playerViewModel: PlayerViewModel,
    onSettingsClick: () -> Unit = {}
) {
    val songs by musicViewModel.songs.collectAsState()
    val sortMode by musicViewModel.sortMode.collectAsState()
    val isLoading by musicViewModel.isLoading.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playlists by musicViewModel.playlists.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    val sortedSongs = musicViewModel.getSortedSongs()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Dunda", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${songs.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            actions = {
                // Sort button
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Sort")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = when (mode) {
                                            SortMode.TITLE -> "Title"
                                            SortMode.ARTIST -> "Artist"
                                            SortMode.DATE_ADDED -> "Recently Added"
                                            SortMode.BPM -> "BPM"
                                        },
                                        color = if (mode == sortMode) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                musicViewModel.setSortMode(mode)
                                showSortMenu = false
                            }
                        )
                    }
                }

                // Refresh
                IconButton(onClick = { musicViewModel.loadSongs() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }

                // Settings
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (sortedSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No music found on your device",
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
                items(sortedSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
                        isCurrentSong = song.id == currentSong?.id,
                        playlists = playlists,
                        onClick = {
                            playerViewModel.playSong(song, sortedSongs)
                        },
                        onAddToPlaylist = { playlistId ->
                            musicViewModel.addSongToPlaylist(playlistId, song.id)
                        }
                    )
                }
            }
        }
    }
}
