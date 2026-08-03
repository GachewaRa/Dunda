package com.dunda.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dunda.app.data.local.SettingsStore
import com.dunda.app.data.model.Song
import com.dunda.app.player.MusicService
import com.dunda.app.player.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode

    private val _isSoloMode = MutableStateFlow(false)
    val isSoloMode: StateFlow<Boolean> = _isSoloMode

    private val _crossfadeDuration = MutableStateFlow(10_000L)
    val crossfadeDuration: StateFlow<Long> = _crossfadeDuration

    private var musicService: MusicService? = null
    private var bound = false

    private val stateListener: () -> Unit = {
        updateState()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            attachService()
        }

        // MediaSessionService returns a null binder for plain bindings, so this
        // (not onServiceConnected) is the callback that actually fires (API 28+).
        // The service is still created by BIND_AUTO_CREATE; reach it via the
        // singleton.
        override fun onNullBinding(name: ComponentName?) {
            attachService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService?.removeStateListener(stateListener)
            musicService = null
            bound = false
        }
    }

    private fun attachService() {
        MusicService.instance?.let {
            if (musicService !== it) {
                musicService?.removeStateListener(stateListener)
                musicService = it
                it.addStateListener(stateListener)
            }
        }
        updateState()
    }

    /**
     * The service reference, attaching lazily if the binding callbacks haven't
     * delivered it yet (e.g. API 26/27 where onNullBinding doesn't exist, or a
     * tap that races service creation).
     */
    private fun service(): MusicService? {
        if (musicService == null) attachService()
        return musicService
    }

    init {
        bindService()
        viewModelScope.launch {
            SettingsStore(application).crossfadeMs.collect { _crossfadeDuration.value = it }
        }
    }

    private fun bindService() {
        val context = getApplication<Application>()
        val intent = Intent(context, MusicService::class.java)
        // Plain startService, NOT startForegroundService: the latter demands a
        // foreground notification within seconds, but Media3 only posts one
        // once playback starts — the unfulfilled promise surfaces as an ANR.
        // MediaSessionService promotes itself to foreground when playback begins.
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        bound = true
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        service()?.playSong(song, queue)
        updateState()
    }

    fun playShuffled(queue: List<Song>) {
        service()?.playShuffled(queue)
        updateState()
    }

    fun playPause() {
        service()?.playPause()
        updateState()
    }

    fun skipNext() {
        service()?.skipNext()
        updateState()
    }

    fun skipPrevious() {
        service()?.skipPrevious()
        updateState()
    }

    fun seekTo(positionMs: Long) {
        service()?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        service()?.toggleShuffle()
        updateState()
    }

    fun cycleRepeatMode() {
        service()?.cycleRepeatMode()
        updateState()
    }

    fun toggleSoloMode() {
        service()?.toggleSoloMode()
        updateState()
    }

    fun setCrossfadeDuration(durationMs: Long) {
        _crossfadeDuration.value = durationMs
        service()?.setCrossfadeDuration(durationMs)
    }

    fun updateState() {
        // Uses the field, not service(): attachService() calls this, so going
        // through service() here would recurse while the service is still null.
        musicService?.let { service ->
            _currentSong.value = service.getCurrentSong()
            _isPlaying.value = service.isPlaying()
            _currentPosition.value = service.currentPosition()
            _duration.value = service.duration()
            _isShuffleEnabled.value = service.isShuffleEnabled()
            _repeatMode.value = service.getRepeatMode()
            _isSoloMode.value = service.isSoloMode()
        }
    }

    /**
     * Call this periodically from the UI to update the position slider.
     * Also refreshes isPlaying so the play/pause icon can never wedge on a
     * stale value (the service pushes changes too, but this is the backstop).
     */
    fun tickPosition() {
        service()?.let {
            _currentPosition.value = it.currentPosition()
            _duration.value = it.duration()
            _isPlaying.value = it.isPlaying()
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicService?.removeStateListener(stateListener)
        if (bound) {
            getApplication<Application>().unbindService(serviceConnection)
            bound = false
        }
    }
}
