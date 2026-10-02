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
import androidx.compose.material.icons.filled.Album
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

/** Album strings that are folder junk, not real album tags. */
val NON_ALBUMS = setOf("audio", "download", "downloads", "music", "<unknown>", "")

fun albumDisplayName(album: String): String =
    if (album.lowercase() in NON_ALBUMS) "Unknown album" else album

private data class AlbumRow(val album: String, val artist: String, val songCount: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    musicViewModel: MusicViewModel,
    onAlbumClick: (String) -> Unit
) {
    val songs by musicViewModel.songs.collectAsState()

    val albums = remember(songs) {
        songs.groupBy { it.album }
            .map { (album, albumSongs) ->
                val artists = albumSongs.map { it.artist }.distinct()
                AlbumRow(
                    album = album,
                    artist = if (artists.size == 1) artistDisplayName(artists[0])
                             else "Various artists",
                    songCount = albumSongs.size,
                )
            }
            .sortedWith(
                compareBy<AlbumRow> { it.album.lowercase() in NON_ALBUMS }
                    .thenBy { it.album.lowercase() }
            )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Albums", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${albums.size} albums",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (albums.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No music found on your device",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(albums, key = { it.album }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlbumClick(row.album) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = albumDisplayName(row.album),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${row.artist} • " +
                                    if (row.songCount == 1) "1 song" else "${row.songCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
