package com.dunda.app.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.dunda.app.R
import com.dunda.app.data.local.AppDatabase
import com.dunda.app.data.local.SettingsStore
import com.dunda.app.data.model.PlayEvent
import com.dunda.app.data.model.QueueState
import com.dunda.app.data.model.Song
import com.dunda.app.data.model.toSong
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

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

        // Keep UI and notification in sync with actual player state.
        crossfadePlayer.setOnPlaybackChanged { notifyStateChanged() }

        // The audible ExoPlayer changes on every crossfade; the session must
        // follow it or lock screen / headphone controls act on the silent one.
        crossfadePlayer.setOnActivePlayerChanged { active ->
            mediaSession?.setPlayer(sessionPlayerFor(active))
        }

        val player = crossfadePlayer.getActivePlayer() ?: ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, sessionPlayerFor(player))
            .setCallback(sessionCallback)
            .build()
        // The service only manages a session's media notification once the
        // session is added to it. Normally a connecting MediaController does
        // this implicitly via onGetSession — but our UI reaches the service
        // through the singleton, so no controller ever connects and, without
        // this call, the notification (shade + lock screen) is never posted.
        mediaSession?.let { addSession(it) }
        updateCustomLayout()

        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        serviceScope.launch {
            settings.crossfadeMs.collect { crossfadePlayer.crossfadeDurationMs = it }
        }
        serviceScope.launch {
            database.songDao().getFavourites().collect { favs ->
                favouriteIds = favs.mapTo(mutableSetOf()) { it.id }
                updateCustomLayout()
                notifyStateChanged()
            }
        }

        restorePersistedState()
        trackerHandler.post(trackerRunnable)
    }

    // ---- media session (lock screen, notification, headphone buttons) ----

    private val cmdToggleFavourite = SessionCommand("dunda.TOGGLE_FAVOURITE", Bundle.EMPTY)
    private val cmdCycleRepeat = SessionCommand("dunda.CYCLE_REPEAT", Bundle.EMPTY)

    private var favouriteIds: Set<Long> = emptySet()
    private val currentIsFavourite: Boolean
        get() = getCurrentSong()?.id?.let { it in favouriteIds } == true

    /**
     * Session-facing wrapper around the currently audible ExoPlayer. A single
     * ExoPlayer only ever holds one MediaItem here (the queue lives in
     * QueueManager), so it doesn't advertise next/previous by itself — this
     * wrapper adds those commands and routes them, plus play/pause, through the
     * service so focus handling and the advance policy always apply.
     */
    private fun sessionPlayerFor(player: ExoPlayer): Player =
        object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean =
                command == Player.COMMAND_SEEK_TO_NEXT ||
                    command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                    super.isCommandAvailable(command)

            override fun seekToNext() = skipNext()
            override fun seekToNextMediaItem() = skipNext()
            override fun seekToPrevious() = skipPrevious()
            override fun seekToPreviousMediaItem() = skipPrevious()
            override fun play() = resumePlayback()
            override fun pause() = pausePlayback()
            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) resumePlayback() else pausePlayback()
            }
        }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(cmdToggleFavourite)
                    .add(cmdCycleRepeat)
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                cmdToggleFavourite.customAction -> toggleCurrentFavourite()
                cmdCycleRepeat.customAction -> cycleRepeatMode()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun updateCustomLayout() {
        val favourite = CommandButton.Builder()
            .setDisplayName(if (currentIsFavourite) "Remove from favourites" else "Add to favourites")
            .setIconResId(
                if (currentIsFavourite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            .setSessionCommand(cmdToggleFavourite)
            .build()
        val repeat = CommandButton.Builder()
            .setDisplayName(
                when (queueManager.repeatMode) {
                    RepeatMode.OFF -> "Repeat off"
                    RepeatMode.ALL -> "Repeat all"
                    RepeatMode.ONE -> "Repeat one"
                    RepeatMode.ONCE -> "Repeat once"
                }
            )
            .setIconResId(
                // Four distinct glyphs: notification icons are uniformly
                // tinted, so state must be legible from shape alone.
                when (queueManager.repeatMode) {
                    RepeatMode.OFF -> R.drawable.ic_repeat
                    RepeatMode.ALL -> R.drawable.ic_repeat_on
                    RepeatMode.ONE -> R.drawable.ic_repeat_one_on
                    RepeatMode.ONCE -> R.drawable.ic_repeat_one
                }
            )
            .setSessionCommand(cmdCycleRepeat)
            .build()
        mediaSession?.setCustomLayout(listOf(favourite, repeat))
    }

    private fun toggleCurrentFavourite() {
        val id = getCurrentSong()?.id ?: return
        val newValue = !currentIsFavourite
        serviceScope.launch {
            withContext(Dispatchers.IO) { database.songDao().setFavourite(id, newValue) }
            // favourites flow collection updates the layout + listeners
        }
    }

    // ---- audio focus ----

    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // A call or another transient sound: pause, and remember to
                // resume ONLY because we paused ourselves — never mid-call.
                resumeOnFocusGain = crossfadePlayer.isPlaying()
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                crossfadePlayer.volumeMultiplier = 0.3f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                crossfadePlayer.volumeMultiplier = 1f
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    crossfadePlayer.resume()
                    notifyStateChanged()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
            .also { focusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    /** Headphones unplugged: pause rather than blast the speaker. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                resumeOnFocusGain = false
                pausePlayback()
            }
        }
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
        updateCustomLayout()   // favourite button reflects the new song
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
        // Focus-gated: during a call the request is denied and nothing starts.
        if (!requestAudioFocus()) return
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
        if (crossfadePlayer.isPlaying()) pausePlayback() else resumePlayback()
    }

    fun pausePlayback() {
        crossfadePlayer.pause()
        persistState()
        notifyStateChanged()
    }

    fun resumePlayback() {
        if (getCurrentSong() == null) return
        if (!requestAudioFocus()) return
        crossfadePlayer.volumeMultiplier = 1f
        crossfadePlayer.resume()
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
        updateCustomLayout()
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
                    .setArtworkUri(song.albumArtUri)
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
        unregisterReceiver(noisyReceiver)
        abandonAudioFocus()
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
