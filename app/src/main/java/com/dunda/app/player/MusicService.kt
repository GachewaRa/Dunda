package com.dunda.app.player

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.dunda.app.data.local.AppDatabase
import com.dunda.app.data.local.SettingsStore
import com.dunda.app.data.model.PlayEvent
import com.dunda.app.data.model.QueueState
import com.dunda.app.data.model.Song
import com.dunda.app.data.model.toSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var crossfadePlayer: CrossfadePlayer

    private val queueManager = QueueManager<Song>()
    private lateinit var playTracker: PlayTracker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Independent of serviceScope so the final persistState in onDestroy is not
    // cancelled with the service.
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase
    private lateinit var settings: SettingsStore

    companion object {
        var instance: MusicService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        settings = SettingsStore(this)

        playTracker = PlayTracker(onQualified = { songId, at ->
            serviceScope.launch(Dispatchers.IO) {
                database.playEventDao().insert(PlayEvent(songId = songId, playedAt = at))
            }
        })

        crossfadePlayer = CrossfadePlayer(this)
        crossfadePlayer.initialize()

        // Crossfade completed: the preloaded item is now audible. Commit the
        // same transition QueueManager promised via peekNext.
        crossfadePlayer.setOnSongTransition {
            queueManager.advanceOnCompletion()
            onCurrentSongStarted()
        }

        // Song ended with nothing preloaded (no crossfade happened).
        crossfadePlayer.setOnPlaybackComplete {
            when (queueManager.advanceOnCompletion()) {
                is QueueManager.NextAction.Play,
                QueueManager.NextAction.RepeatCurrent -> playCurrentSong()
                QueueManager.NextAction.Stop -> {
                    persistState()
                    notifyStateChanged()
                }
            }
        }

        val player = crossfadePlayer.getActivePlayer() ?: ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()

        serviceScope.launch {
            settings.crossfadeMs.collect { crossfadePlayer.crossfadeDurationMs = it }
        }

        restorePersistedState()
        trackerHandler.post(trackerRunnable)
    }

    // ---- play tracking (docs/FEATURES.md §5) ----

    private val trackerHandler = Handler(Looper.getMainLooper())
    private val trackerRunnable = object : Runnable {
        override fun run() {
            if (crossfadePlayer.isPlaying()) playTracker.onProgress(1000)
            trackerHandler.postDelayed(this, 1000)
        }
    }

    /** Common path for every audible song start: new instance + preload + persist. */
    private fun onCurrentSongStarted() {
        getCurrentSong()?.let { playTracker.startInstance(it.id, it.duration) }
        queueNextForCrossfade()
        persistState()
        notifyStateChanged()
    }

    // ---- queue / playback API ----

    fun getCurrentSong(): Song? = queueManager.current
    fun getQueue(): List<Song> = queueManager.queue
    fun getCurrentIndex(): Int = queueManager.currentIndex
    fun isShuffleEnabled(): Boolean = queueManager.shuffleEnabled
    fun getRepeatMode(): RepeatMode = queueManager.repeatMode
    fun isSoloMode(): Boolean = queueManager.soloMode

    fun playSong(song: Song, songList: List<Song> = listOf(song)) {
        val start = songList.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        queueManager.setQueue(songList, start)
        playCurrentSong()
    }

    fun playAtIndex(index: Int) {
        if (queueManager.jumpTo(index)) playCurrentSong()
    }

    /** Start playing [songList] shuffled: random first song, true-shuffle cycle. */
    fun playShuffled(songList: List<Song>) {
        if (songList.isEmpty()) return
        queueManager.setShuffle(true)
        queueManager.setQueue(songList, songList.indices.random())
        playCurrentSong()
    }

    private fun playCurrentSong() {
        val song = getCurrentSong() ?: return
        crossfadePlayer.play(buildMediaItem(song))
        onCurrentSongStarted()
    }

    /**
     * Preload (or clear) the crossfade slot according to the advance policy.
     * RepeatCurrent preloads the same song from its start; Stop (solo mode,
     * end of queue) clears the slot so the song ends cleanly.
     */
    private fun queueNextForCrossfade() {
        when (val next = queueManager.peekNext()) {
            is QueueManager.NextAction.Play ->
                crossfadePlayer.queueNext(buildMediaItem(next.item))
            QueueManager.NextAction.RepeatCurrent ->
                getCurrentSong()?.let { crossfadePlayer.queueNext(buildMediaItem(it)) }
            QueueManager.NextAction.Stop ->
                crossfadePlayer.clearNext()
        }
    }

    fun playPause() {
        if (crossfadePlayer.isPlaying()) {
            crossfadePlayer.pause()
            persistState()
        } else {
            crossfadePlayer.resume()
        }
        notifyStateChanged()
    }

    fun skipNext() {
        when (queueManager.manualNext()) {
            is QueueManager.NextAction.Play,
            QueueManager.NextAction.RepeatCurrent -> playCurrentSong()
            QueueManager.NextAction.Stop -> notifyStateChanged()
        }
    }

    fun skipPrevious() {
        // If more than 3 seconds in, restart the song
        if (crossfadePlayer.currentPosition() > 3000) {
            crossfadePlayer.seekTo(0)
            return
        }
        when (queueManager.manualPrevious()) {
            is QueueManager.NextAction.Play,
            QueueManager.NextAction.RepeatCurrent -> playCurrentSong()
            QueueManager.NextAction.Stop -> crossfadePlayer.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        crossfadePlayer.seekTo(positionMs)
    }

    fun addToQueue(song: Song) {
        queueManager.addToQueue(song)
        queueNextForCrossfade()
        persistState()
        notifyStateChanged()
    }

    // ---- modes ----

    fun toggleShuffle() {
        queueManager.toggleShuffle()
        onModeChanged()
    }

    fun setRepeatMode(mode: RepeatMode) {
        queueManager.repeatMode = mode
        onModeChanged()
    }

    fun cycleRepeatMode() {
        val next = when (queueManager.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ONCE
            RepeatMode.ONCE -> RepeatMode.OFF
        }
        setRepeatMode(next)
    }

    fun toggleSoloMode() {
        queueManager.soloMode = !queueManager.soloMode
        onModeChanged()
    }

    /** A mode change can invalidate what's preloaded for crossfade. */
    private fun onModeChanged() {
        queueNextForCrossfade()
        persistState()
        notifyStateChanged()
    }

    fun setCrossfadeDuration(durationMs: Long) {
        crossfadePlayer.crossfadeDurationMs = durationMs
        serviceScope.launch { settings.setCrossfadeMs(durationMs) }
    }

    fun isPlaying(): Boolean = crossfadePlayer.isPlaying()
    fun currentPosition(): Long = crossfadePlayer.currentPosition()
    fun duration(): Long = crossfadePlayer.duration()

    // ---- persistence (docs/FEATURES.md §8) ----

    private fun persistState() {
        val snap = queueManager.snapshot()
        val queueIds = queueManager.queue.joinToString(",") { it.id.toString() }
        val state = QueueState(
            queueIds = queueIds,
            shuffleOrder = snap.order.joinToString(","),
            shuffleCursor = snap.cursor,
            currentIndex = snap.currentIndex,
            positionMs = crossfadePlayer.currentPosition(),
            shuffleEnabled = snap.shuffleEnabled,
            repeatMode = snap.repeatMode.name,
            soloMode = snap.soloMode,
        )
        persistScope.launch { database.queueStateDao().save(state) }
    }

    private fun restorePersistedState() {
        serviceScope.launch {
            val restored = kotlin.runCatching {
                val state = database.queueStateDao().get() ?: return@launch
                val songsById = database.songDao().getAllPresentOnce().associateBy { it.id }
                val ids = state.queueIds.split(",").mapNotNull { it.toLongOrNull() }
                val songs = ids.mapNotNull { songsById[it]?.toSong() }
                if (songs.isEmpty()) return@launch
                // Index-based fields survive only if no song vanished; otherwise
                // fall back to a fresh queue at the same current song.
                if (songs.size == ids.size) {
                    queueManager.restore(
                        songs,
                        QueueManager.Snapshot(
                            order = state.shuffleOrder.split(",").mapNotNull { it.toIntOrNull() },
                            cursor = state.shuffleCursor,
                            currentIndex = state.currentIndex,
                            shuffleEnabled = state.shuffleEnabled,
                            repeatMode = RepeatMode.entries.firstOrNull { it.name == state.repeatMode }
                                ?: RepeatMode.OFF,
                            soloMode = state.soloMode,
                        )
                    )
                } else {
                    queueManager.setQueue(songs, 0)
                }
                // Restore paused at the saved song/position; user resumes explicitly.
                getCurrentSong()?.let { song ->
                    crossfadePlayer.prepareAt(buildMediaItem(song), state.positionMs)
                    playTracker.startInstance(song.id, song.duration)
                    queueNextForCrossfade()
                }
                notifyStateChanged()
            }
            restored.exceptionOrNull()?.let {
                // Corrupt state must never block startup; start clean instead.
                kotlin.runCatching { database.queueStateDao().clear() }
            }
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
        persistState()
        trackerHandler.removeCallbacks(trackerRunnable)
        serviceScope.cancel()
        crossfadePlayer.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
