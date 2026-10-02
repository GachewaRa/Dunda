package com.dunda.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
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

            Spacer(modifier = Modifier.height(24.dp))

            // ---- Background reliability ----
            val context = LocalContext.current
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            var exempt by remember {
                mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep player alive in background",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (exempt) {
                            "Battery optimization is off for Dunda — the paused player should stay available."
                        } else {
                            "Your phone may kill the paused player to save battery, making the notification disappear. Tap to exempt Dunda from battery optimization."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = exempt,
                    onCheckedChange = {
                        if (!exempt) {
                            @SuppressLint("BatteryLife")
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                        // Re-check on return; the system dialog decides the truth
                        exempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            BackupSection(musicViewModel)
        }
    }
}

@Composable
private fun BackupSection(musicViewModel: MusicViewModel) {
    val backupStatus by musicViewModel.backupStatus.collectAsState()
    val lastBackupAt by musicViewModel.lastBackupAt.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { musicViewModel.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { musicViewModel.importBackup(it) } }

    Text(text = "Backup & restore", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Saves favourites, playlists, play history, edited titles and settings " +
            "to a single file. Pick your Google Drive folder in the file picker and " +
            "it syncs to the cloud automatically.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    if (lastBackupAt > 0) {
        Text(
            text = "Last backup: " + java.text.SimpleDateFormat(
                "d MMM yyyy, HH:mm", java.util.Locale.getDefault()
            ).format(java.util.Date(lastBackupAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Row {
        TextButton(onClick = {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(java.util.Date())
            exportLauncher.launch("dunda-backup-$stamp.json")
        }) { Text("Back up now") }
        TextButton(onClick = {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
        }) { Text("Restore…") }
    }
    backupStatus?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
