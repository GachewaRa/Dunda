package com.dunda.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dunda.app.viewmodel.MusicViewModel
import com.dunda.app.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    playerViewModel: PlayerViewModel,
    musicViewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val crossfadeDuration by playerViewModel.crossfadeDuration.collectAsState()
    val minDurationMs by musicViewModel.minDurationMs.collectAsState()
    val excludeNonMusic by musicViewModel.excludeNonMusic.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Crossfade Duration",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = crossfadeDuration.toFloat() / 1000f,
                    onValueChange = { seconds ->
                        playerViewModel.setCrossfadeDuration((seconds * 1000).toLong())
                    },
                    valueRange = 0f..30f,
                    steps = 29,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Text(
                    text = "${(crossfadeDuration / 1000)}s",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Songs will start transitioning this many seconds before the end. Set to 0 to disable crossfade.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ---- Library filters ----
            Text(
                text = "Minimum song length",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = (minDurationMs / 1000f),
                    onValueChange = { seconds ->
                        musicViewModel.setMinDurationMs((seconds.toLong() * 1000))
                    },
                    valueRange = 0f..300f,
                    steps = 29,   // 10-second increments
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                val secs = minDurationMs / 1000
                Text(
                    text = if (secs == 0L) "Off" else "%d:%02d".format(secs / 60, secs % 60),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Audio shorter than this is left out of your library (jingles, snippets). Set to 0 to include everything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hide voice notes & recordings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Filters out WhatsApp voice notes, call and screen recordings, and similar non-music audio by their file signatures.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = excludeNonMusic,
                    onCheckedChange = { musicViewModel.setExcludeNonMusic(it) }
                )
            }
        }
    }
}
