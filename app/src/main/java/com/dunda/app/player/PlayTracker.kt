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

    var activeSongId: Long = -1
        private set
    private var durationMs: Long = 0
    var accumulatedMs: Long = 0
        private set
    var isQualified: Boolean = false
        private set

    /** Call on every song start AND every restart of the same song. */
    fun startInstance(songId: Long, durationMs: Long) {
        this.activeSongId = songId
        this.durationMs = durationMs
        accumulatedMs = 0
        isQualified = false
    }

    /**
     * Resume a previously persisted instance so listening time survives
     * process death (pause at 0:16, app killed, reopen → still 16s in).
     */
    fun restoreInstance(songId: Long, durationMs: Long, accumulatedMs: Long, qualified: Boolean) {
        this.activeSongId = songId
        this.durationMs = durationMs
        this.accumulatedMs = accumulatedMs.coerceAtLeast(0)
        this.isQualified = qualified
    }

    /** Call periodically with wall-clock listened time since the last call. */
    fun onProgress(listenedDeltaMs: Long) {
        if (isQualified || activeSongId < 0 || listenedDeltaMs <= 0) return
        accumulatedMs += listenedDeltaMs
        val threshold = if (durationMs > 0) minOf(QUALIFY_CAP_MS, durationMs / 2) else QUALIFY_CAP_MS
        if (accumulatedMs >= threshold) {
            isQualified = true
            onQualified(activeSongId, clock())
        }
    }

    fun reset() {
        activeSongId = -1
        durationMs = 0
        accumulatedMs = 0
        isQualified = false
    }
}
