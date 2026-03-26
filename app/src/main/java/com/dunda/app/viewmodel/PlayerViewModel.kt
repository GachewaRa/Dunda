package com.dunda.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import com.dunda.app.data.model.Song
import com.dunda.app.player.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    private val _crossfadeDuration = MutableStateFlow(10_000L)
    val crossfadeDuration: StateFlow<Long> = _crossfadeDuration

    private var musicService: MusicService? = null
    private var bound = false

    private val stateListener: () -> Unit = {
        updateState()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            // We use the singleton pattern since MediaSessionService doesn't give us a binder easily
            musicService = MusicService.instance
            musicService?.addStateListener(stateListener)
            updateState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService?.removeStateListener(stateListener)
            musicService = null
            bound = false
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val context = getApplication<Application>()
        val intent = Intent(context, MusicService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        bound = true
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        musicService?.playSong(song, queue)
        updateState()
    }

    fun playPause() {
        musicService?.playPause()
        updateState()
    }

    fun skipNext() {
        musicService?.skipNext()
        updateState()
    }

    fun skipPrevious() {
        musicService?.skipPrevious()
        updateState()
    }

    fun seekTo(positionMs: Long) {
        musicService?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        musicService?.toggleShuffle()
        updateState()
    }

    fun setCrossfadeDuration(durationMs: Long) {
        _crossfadeDuration.value = durationMs
        musicService?.setCrossfadeDuration(durationMs)
    }

    fun updateState() {
        musicService?.let { service ->
            _currentSong.value = service.getCurrentSong()
            _isPlaying.value = service.isPlaying()
            _currentPosition.value = service.currentPosition()
            _duration.value = service.duration()
            _isShuffleEnabled.value = service.isShuffleEnabled()
        }
    }

    /**
     * Call this periodically from the UI to update the position slider.
     */
    fun tickPosition() {
        musicService?.let {
            _currentPosition.value = it.currentPosition()
            _duration.value = it.duration()
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
