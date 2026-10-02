package com.dunda.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dunda.app.viewmodel.PlayerViewModel

/**
 * The play queue: shows what's coming, jump by tapping, reorder with the
 * arrows, remove songs. Under shuffle, reordering changes the visible list
 * but not the shuffle playback order (which is the point of shuffle).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val queue by playerViewModel.queue.collectAsState()
    val queueIndex by playerViewModel.queueIndex.collectAsState()
    val listState = rememberLazyListState()

    // Open scrolled to the playing song
    LaunchedEffect(Unit) {
        if (queueIndex > 1) listState.scrollToItem(queueIndex - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Queue", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${queue.size} songs" +
                            if (queueIndex >= 0) " • playing #${queueIndex + 1}" else "",
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Queue is empty.\nPlay something to fill it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(queue, key = { i, s -> "$i-${s.id}" }) { index, song ->
                    val isCurrent = index == queueIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playerViewModel.playAtIndex(index) }
                            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCurrent) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Playing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(32.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.width(32.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { playerViewModel.moveInQueue(index, index - 1) },
                            enabled = index > 0
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                tint = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (index > 0) 0.6f else 0.2f
                                )
                            )
                        }
                        IconButton(
                            onClick = { playerViewModel.moveInQueue(index, index + 1) },
                            enabled = index < queue.size - 1
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                tint = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (index < queue.size - 1) 0.6f else 0.2f
                                )
                            )
                        }
                        IconButton(
                            onClick = { playerViewModel.removeFromQueue(index) },
                            enabled = !isCurrent
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove from queue",
                                tint = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (!isCurrent) 0.6f else 0.2f
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
