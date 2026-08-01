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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dunda.app.data.local.SongPlayStats
import com.dunda.app.data.model.toSong
import com.dunda.app.viewmodel.MusicViewModel
import com.dunda.app.viewmodel.PlayerViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant

enum class StatsPeriod(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    YEAR("This year"),
    ALL_TIME("All time");

    /** Epoch-millis range [from, to] for this period, in the device zone. */
    fun range(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = when (this) {
            WEEK -> today.minusDays(today.dayOfWeek.value.toLong() - 1)   // Monday
            MONTH -> today.withDayOfMonth(1)
            YEAR -> today.withDayOfYear(1)
            ALL_TIME -> null
        }
        val fromMs = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: 0L
        return fromMs to Long.MAX_VALUE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    musicViewModel: MusicViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    var mostFirst by rememberSaveable { mutableStateOf(true) }

    val (from, to) = remember(period) { period.range() }
    val stats by remember(from, to) { musicViewModel.statsInRange(from, to) }
        .collectAsState(initial = emptyList())

    val ranked = remember(stats, mostFirst) {
        val comparator = if (mostFirst) {
            compareByDescending<SongPlayStats> { it.playCount }
                .thenBy { it.song.title.lowercase() }
        } else {
            compareBy<SongPlayStats> { it.playCount }
                .thenBy { it.song.title.lowercase() }
        }
        stats.sortedWith(comparator)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Statistics", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p },
                    label = { Text(p.label) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = mostFirst,
                onClick = { mostFirst = true },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Most played") }
            SegmentedButton(
                selected = !mostFirst,
                onClick = { mostFirst = false },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Least played") }
        }

        if (ranked.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No listening data yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            val allSongs = remember(ranked) { ranked.map { it.song.toSong() } }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(ranked, key = { _, s -> s.song.id }) { index, stat ->
                    StatsRow(
                        rank = index + 1,
                        stat = stat,
                        onClick = {
                            playerViewModel.playSong(stat.song.toSong(), allSongs)
                        }
                    )
                }
            }
        }
    }
}

private val lastPlayedFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
private fun StatsRow(rank: Int, stat: SongPlayStats, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(36.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
        ) {
            Text(
                text = stat.song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val lastPlayed = stat.lastPlayedAt?.let {
                "last " + Instant.ofEpochMilli(it)
                    .atZone(ZoneId.systemDefault()).toLocalDate().format(lastPlayedFormat)
            }
            Text(
                text = listOfNotNull(stat.song.artist, lastPlayed).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (stat.playCount == 1) "1 play" else "${stat.playCount} plays",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
