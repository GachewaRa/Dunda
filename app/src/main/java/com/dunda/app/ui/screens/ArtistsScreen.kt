package com.dunda.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.dunda.app.viewmodel.MusicViewModel

const val UNKNOWN_ARTIST = "<unknown>"

fun artistDisplayName(artist: String): String =
    if (artist == UNKNOWN_ARTIST || artist.isBlank()) "Unknown artist" else artist

private data class ArtistRow(val artist: String, val songCount: Int, val playCount: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    musicViewModel: MusicViewModel,
    onArtistClick: (String) -> Unit
) {
    val songs by musicViewModel.songs.collectAsState()
    val playCounts by musicViewModel.playCounts.collectAsState()

    val artists = remember(songs, playCounts) {
        songs.groupBy { it.artist }
            .map { (artist, artistSongs) ->
                ArtistRow(
                    artist = artist,
                    songCount = artistSongs.size,
                    playCount = artistSongs.sumOf { playCounts[it.id] ?: 0 },
                )
            }
            // Alphabetical, with the unknown-artist bucket last
            .sortedWith(
                compareBy<ArtistRow> { it.artist == UNKNOWN_ARTIST }
                    .thenBy { it.artist.lowercase() }
            )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Artists", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${artists.size} artists",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (artists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No music found on your device",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(artists, key = { it.artist }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArtistClick(row.artist) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artistDisplayName(row.artist),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val songsLabel =
                                if (row.songCount == 1) "1 song" else "${row.songCount} songs"
                            val playsLabel =
                                if (row.playCount == 1) "1 play" else "${row.playCount} plays"
                            Text(
                                text = "$songsLabel • $playsLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
