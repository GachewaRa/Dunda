package com.dunda.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only log of qualified plays. A play qualifies when cumulative listened
 * time reaches 30s or 50% of the song's duration, whichever is smaller (see
 * player/PlayTracker). Counts are always derived by aggregating this table over
 * a date range — never stored as mutable counters — so "most played this month"
 * and similar queries stay answerable.
 */
@Entity(
    tableName = "play_events",
    indices = [Index("songId"), Index("playedAt")]
)
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long,   // epoch millis at the moment the play qualified
)
