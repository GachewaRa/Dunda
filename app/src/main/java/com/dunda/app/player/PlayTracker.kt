package com.dunda.app.player

/**
 * Decides when a playback instance counts as a "play" (docs/FEATURES.md §5):
 * once cumulative listened time reaches 30 seconds or 50% of the song's
 * duration, whichever is smaller. Each restart of the same song (repeat
 * ONE/ONCE loops included) is a new instance and can log a new play.
 * Seeking doesn't reset accumulated time. Pure JVM — unit-tested.
 */
class PlayTracker(
    private val onQualified: (songId: Long, atEpochMs: Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val QUALIFY_CAP_MS = 30_000L
    }

    private var songId: Long = -1
    private var durationMs: Long = 0
    private var accumulatedMs: Long = 0
    private var qualified = false

    /** Call on every song start AND every restart of the same song. */
    fun startInstance(songId: Long, durationMs: Long) {
        this.songId = songId
        this.durationMs = durationMs
        accumulatedMs = 0
        qualified = false
    }

    /** Call periodically with wall-clock listened time since the last call. */
    fun onProgress(listenedDeltaMs: Long) {
        if (qualified || songId < 0 || listenedDeltaMs <= 0) return
        accumulatedMs += listenedDeltaMs
        val threshold = if (durationMs > 0) minOf(QUALIFY_CAP_MS, durationMs / 2) else QUALIFY_CAP_MS
        if (accumulatedMs >= threshold) {
            qualified = true
            onQualified(songId, clock())
        }
    }

    fun reset() {
        songId = -1
        durationMs = 0
        accumulatedMs = 0
        qualified = false
    }
}
