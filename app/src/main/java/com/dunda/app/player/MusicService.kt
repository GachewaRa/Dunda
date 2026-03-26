package com.dunda.app.player

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dunda.app.data.model.Song

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var crossfadePlayer: CrossfadePlayer

    // Current queue state
    private val queue = mutableListOf<Song>()
    private var currentIndex = -1
    private var shuffleEnabled = false
    private var shuffledIndices = mutableListOf<Int>()

    companion object {
        var instance: MusicService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        crossfadePlayer = CrossfadePlayer(this)
        crossfadePlayer.initialize()

        crossfadePlayer.setOnSongTransition {
            currentIndex = getNextIndex()
            queueNextForCrossfade()
            notifyStateChanged()
        }

        crossfadePlayer.setOnPlaybackComplete {
            val nextIdx = getNextIndex()
            if (nextIdx >= 0) {
                currentIndex = nextIdx
                playCurrentSong()
            } else {
                notifyStateChanged()
            }
        }

        // Create a MediaSession using the active ExoPlayer
        val player = crossfadePlayer.getActivePlayer() ?: ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    fun getCrossfadePlayer(): CrossfadePlayer = crossfadePlayer

    fun getCurrentSong(): Song? {
        if (currentIndex < 0 || currentIndex >= queue.size) return null
        return queue[currentIndex]
    }

    fun getQueue(): List<Song> = queue.toList()
    fun getCurrentIndex(): Int = currentIndex
    fun isShuffleEnabled(): Boolean = shuffleEnabled

    fun playSong(song: Song, songList: List<Song> = listOf(song)) {
        queue.clear()
        queue.addAll(songList)
        currentIndex = queue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0

        if (shuffleEnabled) {
            generateShuffledIndices()
        }

        playCurrentSong()
    }

    fun playAtIndex(index: Int) {
        if (index in queue.indices) {
            currentIndex = index
            playCurrentSong()
        }
    }

    private fun playCurrentSong() {
        val song = getCurrentSong() ?: return
        val mediaItem = buildMediaItem(song)
        crossfadePlayer.play(mediaItem)
        queueNextForCrossfade()
        notifyStateChanged()
    }

    private fun queueNextForCrossfade() {
        val nextIdx = getNextIndex()
        if (nextIdx >= 0 && nextIdx < queue.size) {
            val nextSong = queue[nextIdx]
            crossfadePlayer.queueNext(buildMediaItem(nextSong))
        }
    }

    fun playPause() {
        if (crossfadePlayer.isPlaying()) {
            crossfadePlayer.pause()
        } else {
            crossfadePlayer.resume()
        }
        notifyStateChanged()
    }

    fun skipNext() {
        val nextIdx = getNextIndex()
        if (nextIdx >= 0) {
            currentIndex = nextIdx
            playCurrentSong()
        }
    }

    fun skipPrevious() {
        // If more than 3 seconds in, restart the song
        if (crossfadePlayer.currentPosition() > 3000) {
            crossfadePlayer.seekTo(0)
            return
        }
        val prevIdx = getPreviousIndex()
        if (prevIdx >= 0) {
            currentIndex = prevIdx
            playCurrentSong()
        }
    }

    fun seekTo(positionMs: Long) {
        crossfadePlayer.seekTo(positionMs)
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        if (shuffleEnabled) {
            generateShuffledIndices()
        }
        notifyStateChanged()
    }

    fun setCrossfadeDuration(durationMs: Long) {
        crossfadePlayer.crossfadeDurationMs = durationMs
    }

    fun isPlaying(): Boolean = crossfadePlayer.isPlaying()
    fun currentPosition(): Long = crossfadePlayer.currentPosition()
    fun duration(): Long = crossfadePlayer.duration()

    private fun getNextIndex(): Int {
        if (queue.isEmpty()) return -1
        return if (shuffleEnabled && shuffledIndices.isNotEmpty()) {
            val currentShufflePos = shuffledIndices.indexOf(currentIndex)
            if (currentShufflePos < shuffledIndices.size - 1) {
                shuffledIndices[currentShufflePos + 1]
            } else {
                -1 // end of shuffled queue
            }
        } else {
            if (currentIndex < queue.size - 1) currentIndex + 1 else -1
        }
    }

    private fun getPreviousIndex(): Int {
        if (queue.isEmpty()) return -1
        return if (shuffleEnabled && shuffledIndices.isNotEmpty()) {
            val currentShufflePos = shuffledIndices.indexOf(currentIndex)
            if (currentShufflePos > 0) {
                shuffledIndices[currentShufflePos - 1]
            } else {
                -1
            }
        } else {
            if (currentIndex > 0) currentIndex - 1 else -1
        }
    }

    private fun generateShuffledIndices() {
        shuffledIndices = queue.indices.toMutableList().apply {
            remove(currentIndex)
            shuffle()
            add(0, currentIndex) // current song stays first
        }
    }

    private fun buildMediaItem(song: Song): MediaItem {
        return MediaItem.Builder()
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build()
            )
            .build()
    }

    // Listeners for UI updates
    private val stateListeners = mutableListOf<() -> Unit>()

    fun addStateListener(listener: () -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: () -> Unit) {
        stateListeners.remove(listener)
    }

    private fun notifyStateChanged() {
        stateListeners.forEach { it.invoke() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        instance = null
        crossfadePlayer.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
