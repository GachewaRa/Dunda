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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.Song
import com.dunda.app.ui.components.SongItem
import com.dunda.app.data.model.SortMode
import com.dunda.app.viewmodel.MusicViewModel
import com.dunda.app.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    musicViewModel: MusicViewModel,
    playerViewModel: PlayerViewModel,
    onSettingsClick: () -> Unit = {},
    onStatsClick: () -> Unit = {}
) {
    val songs by musicViewModel.songs.collectAsState()
    val sortMode by musicViewModel.sortMode.collectAsState()
    val isLoading by musicViewModel.isLoading.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playlists by musicViewModel.playlists.collectAsState()
    val playCounts by musicViewModel.playCounts.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val sortedSongs = musicViewModel.sortSongs(songs, sortMode)
    val visibleSongs = if (searchActive && searchQuery.isNotBlank()) {
        sortedSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
        }
    } else sortedSongs

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (searchActive) {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search songs, artists, albums") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                } else {
                    Column {
                        Text("Dunda", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${songs.size} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            actions = {
                // Search
                IconButton(onClick = {
                    searchActive = !searchActive
                    if (!searchActive) searchQuery = ""
                }) {
                    Icon(
                        if (searchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchActive) "Close search" else "Search"
                    )
                }
                // Sort button
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Sort")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortMode.entries.filter { it != SortMode.CUSTOM }.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = sortModeLabel(mode),
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

                // Statistics
                IconButton(onClick = onStatsClick) {
                    Icon(Icons.Default.BarChart, contentDescription = "Statistics")
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
        } else if (visibleSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (searchActive && searchQuery.isNotBlank()) "No matches for \"$searchQuery\""
                    else "No music found on your device",
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
                items(visibleSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
                        isCurrentSong = song.id == currentSong?.id,
                        playlists = playlists,
                        playCount = playCounts[song.id] ?: 0,
                        onClick = {
                            // Queue is what's on screen: search results included
                            playerViewModel.playSong(song, visibleSongs)
                        },
                        onAddToPlaylist = { playlistId ->
                            musicViewModel.addSongToPlaylist(playlistId, song.id)
                        },
                        onToggleFavourite = { musicViewModel.toggleFavourite(song) }
                    )
                }
            }
        }
    }
}
