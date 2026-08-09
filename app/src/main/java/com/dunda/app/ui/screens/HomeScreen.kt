package com.dunda.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onStatsClick: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {}
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

    // Multi-select: long-press enters selection mode, tap toggles membership
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var songToEdit by remember { mutableStateOf<Song?>(null) }

    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    val sortedSongs = musicViewModel.sortSongs(songs, sortMode)
    val visibleSongs = if (searchActive && searchQuery.isNotBlank()) {
        sortedSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
        }
    } else sortedSongs

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            TopAppBar(
                title = { Text("${selectedIds.size} selected") },
                navigationIcon = {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        selectedIds = visibleSongs.mapTo(mutableSetOf()) { it.id }
                    }) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                    }
                    IconButton(onClick = { showAddToPlaylistDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add selected to playlist"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        } else {
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
        }

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
                        isSelected = song.id in selectedIds,
                        onClick = {
                            if (selectionMode) {
                                selectedIds =
                                    if (song.id in selectedIds) selectedIds - song.id
                                    else selectedIds + song.id
                            } else {
                                // Queue is what's on screen: search results included
                                playerViewModel.playSong(song, visibleSongs)
                                onOpenNowPlaying()
                            }
                        },
                        onLongClick = { selectedIds = selectedIds + song.id },
                        onAddToPlaylist = { playlistId ->
                            musicViewModel.addSongToPlaylist(playlistId, song.id)
                        },
                        onToggleFavourite = { musicViewModel.toggleFavourite(song) },
                        onEditInfo = { songToEdit = song }
                    )
                }
            }
        }
    }

    songToEdit?.let { song ->
        var title by remember(song.id) { mutableStateOf(song.title) }
        var artist by remember(song.id) { mutableStateOf(song.artist) }
        AlertDialog(
            onDismissRequest = { songToEdit = null },
            title = { Text("Edit song info") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Artist") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        musicViewModel.setSongInfo(song.id, title, artist)
                        songToEdit = null
                    },
                    enabled = title.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { songToEdit = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Add ${selectedIds.size} songs to…") },
            text = {
                Column {
                    TextButton(onClick = {
                        showAddToPlaylistDialog = false
                        showNewPlaylistDialog = true
                    }) { Text("+ New playlist…") }
                    playlists.forEach { playlist ->
                        TextButton(onClick = {
                            musicViewModel.addSongsToPlaylist(playlist.id, selectedIds.toList())
                            selectedIds = emptySet()
                            showAddToPlaylistDialog = false
                        }) { Text(playlist.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewPlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("New playlist with ${selectedIds.size} songs") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        musicViewModel.createPlaylistWithSongs(name.trim(), selectedIds.toList())
                        selectedIds = emptySet()
                        showNewPlaylistDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }
}
