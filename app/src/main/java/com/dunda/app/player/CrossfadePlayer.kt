package com.dunda.app.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages crossfade transitions between two ExoPlayer instances.
 * When the current song approaches its end (within crossfadeDuration),
 * the next song begins playing with a volume fade-in while the current
 * song fades out.
 */
class CrossfadePlayer(private val context: Context) {

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var activePlayer: ExoPlayer? = null
    private var inactivePlayer: ExoPlayer? = null

    var crossfadeDurationMs: Long = 10_000L  // default 10 seconds
    private var isCrossfading = false

    private val handler = Handler(Looper.getMainLooper())
    private var onSongTransition: (() -> Unit)? = null
    private var onPlaybackComplete: (() -> Unit)? = null

    fun initialize() {
        playerA = ExoPlayer.Builder(context).build()
        playerB = ExoPlayer.Builder(context).build()
        activePlayer = playerA
        inactivePlayer = playerB
    }

    fun getActivePlayer(): ExoPlayer? = activePlayer

    fun setOnSongTransition(callback: () -> Unit) {
        onSongTransition = callback
    }

    fun setOnPlaybackComplete(callback: () -> Unit) {
        onPlaybackComplete = callback
    }

    fun play(mediaItem: MediaItem) {
        stopCrossfade()
        activePlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            volume = 1f
            playWhenReady = true
        }
        startMonitoring()
    }

    fun pause() {
        activePlayer?.playWhenReady = false
        if (isCrossfading) {
            inactivePlayer?.playWhenReady = false
        }
        stopMonitoring()
    }

    fun resume() {
        activePlayer?.playWhenReady = true
        if (isCrossfading) {
            inactivePlayer?.playWhenReady = true
        }
        startMonitoring()
    }

    fun seekTo(positionMs: Long) {
        activePlayer?.seekTo(positionMs)
    }

    fun isPlaying(): Boolean = activePlayer?.isPlaying == true

    fun currentPosition(): Long = activePlayer?.currentPosition ?: 0L

    fun duration(): Long = activePlayer?.duration ?: 0L

    /**
     * Queue the next song for crossfade. Call this when you know what's next.
     */
    fun queueNext(mediaItem: MediaItem) {
        inactivePlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            volume = 0f
            playWhenReady = false
        }
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            val player = activePlayer ?: return
            val duration = player.duration
            val position = player.currentPosition

            if (duration > 0 && position > 0) {
                val remaining = duration - position

                if (remaining <= crossfadeDurationMs && !isCrossfading) {
                    // Check if next song is queued
                    if (inactivePlayer?.mediaItemCount ?: 0 > 0) {
                        startCrossfade()
                    }
                }

                if (isCrossfading) {
                    updateCrossfadeVolumes()
                }

                // Check if active player finished
                if (player.playbackState == Player.STATE_ENDED) {
                    if (isCrossfading) {
                        finishCrossfade()
                    } else {
                        onPlaybackComplete?.invoke()
                        return
                    }
                }
            }

            handler.postDelayed(this, 100) // check every 100ms
        }
    }

    private fun startMonitoring() {
        handler.removeCallbacks(monitorRunnable)
        handler.post(monitorRunnable)
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(monitorRunnable)
    }

    private fun startCrossfade() {
        isCrossfading = true
        inactivePlayer?.apply {
            volume = 0f
            playWhenReady = true
        }
    }

    private fun updateCrossfadeVolumes() {
        val player = activePlayer ?: return
        val remaining = player.duration - player.currentPosition
        val progress = 1f - (remaining.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)

        activePlayer?.volume = (1f - progress).coerceIn(0f, 1f)
        inactivePlayer?.volume = progress.coerceIn(0f, 1f)
    }

    private fun finishCrossfade() {
        isCrossfading = false
        activePlayer?.apply {
            stop()
            clearMediaItems()
        }

        // Swap players
        val temp = activePlayer
        activePlayer = inactivePlayer
        inactivePlayer = temp

        activePlayer?.volume = 1f
        onSongTransition?.invoke()
    }

    private fun stopCrossfade() {
        isCrossfading = false
        inactivePlayer?.apply {
            stop()
            clearMediaItems()
            volume = 0f
        }
    }

    fun release() {
        stopMonitoring()
        playerA?.release()
        playerB?.release()
        playerA = null
        playerB = null
        activePlayer = null
        inactivePlayer = null
    }
}
