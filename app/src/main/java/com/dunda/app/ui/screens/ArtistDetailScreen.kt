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
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dunda.app.ui.components.SongItem
import com.dunda.app.viewmodel.MusicViewModel
import com.dunda.app.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    musicViewModel: MusicViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit = {}
) {
    val allSongs by musicViewModel.songs.collectAsState()
    val playlists by musicViewModel.playlists.collectAsState()
    val playCounts by musicViewModel.playCounts.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()

    val artistSongs = remember(allSongs, artistName) {
        allSongs.filter { it.artist == artistName }
            .sortedBy { it.title.lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        artistDisplayName(artistName),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val plays = artistSongs.sumOf { playCounts[it.id] ?: 0 }
                    Text(
                        "${artistSongs.size} songs • $plays plays",
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
                if (artistSongs.isNotEmpty()) {
                    TextButton(onClick = { playerViewModel.playShuffled(artistSongs) }) {
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

        if (artistSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No songs by this artist",
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
                items(artistSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
                        isCurrentSong = song.id == currentSong?.id,
                        playlists = playlists,
                        playCount = playCounts[song.id] ?: 0,
                        onClick = {
                            playerViewModel.playSong(song, artistSongs)
                            onOpenNowPlaying()
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
