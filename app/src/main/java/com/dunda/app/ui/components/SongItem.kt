package com.dunda.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    isCurrentSong: Boolean = false,
    playlists: List<Playlist> = emptyList(),
    playCount: Int? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onAddToPlaylist: ((Long) -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onToggleFavourite: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art placeholder; selection check when selected
        Icon(
            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.MusicNote,
            contentDescription = if (isSelected) "Selected" else null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .padding(8.dp),
            tint = if (isSelected || isCurrentSong) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Song info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentSong) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                append(song.artist)
                append(" • ")
                append(song.durationFormatted)
                if (playCount != null) {
                    append(" • ")
                    append(if (playCount == 1) "1 play" else "$playCount plays")
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.bpm != null) {
                Text(
                    text = "${song.bpm} BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        if (onToggleFavourite != null) {
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    imageVector = if (song.isFavourite) Icons.Default.Favorite
                                  else Icons.Default.FavoriteBorder,
                    contentDescription = if (song.isFavourite) "Remove from favourites"
                                         else "Add to favourites",
                    tint = if (song.isFavourite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        // More options (add to / remove from playlist)
        val hasAddMenu = onAddToPlaylist != null && playlists.isNotEmpty()
        if (hasAddMenu || onRemoveFromPlaylist != null) {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (hasAddMenu) {
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = { Text("Add to ${playlist.name}") },
                            onClick = {
                                onAddToPlaylist?.invoke(playlist.id)
                                showMenu = false
                            }
                        )
                    }
                }
                if (onRemoveFromPlaylist != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Remove from this playlist",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onRemoveFromPlaylist()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}
