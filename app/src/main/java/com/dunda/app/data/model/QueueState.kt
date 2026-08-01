package com.dunda.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row snapshot of playback state so the queue, shuffle-cycle progress,
 * and modes survive process death. Written by MusicService on meaningful
 * transitions (song change, mode change, pause), restored on service creation.
 */
@Entity(tableName = "queue_state")
data class QueueState(
    @PrimaryKey val id: Int = 0,
    val queueIds: String,        // comma-separated song IDs in queue order
    val shuffleOrder: String,    // comma-separated queue indices; empty when shuffle off
    val shuffleCursor: Int,      // position within shuffleOrder; -1 when shuffle off
    val currentIndex: Int,       // index into queue
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: String,      // RepeatMode enum name
    val soloMode: Boolean,
)
