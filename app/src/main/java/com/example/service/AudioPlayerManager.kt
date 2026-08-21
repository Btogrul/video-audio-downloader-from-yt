package com.example.service

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.model.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AudioPlayerState(
    val currentItem: DownloadItem? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val isLooping: Boolean = false
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedCurrentTime: String
        get() = formatTime(currentPositionMs)

    val formattedDurationTime: String
        get() = formatTime(durationMs)

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%d:%02d", min, sec)
    }
}

class AudioPlayerManager(private val context: Context) {
    private val TAG = "AudioPlayerManager"
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    fun playAudio(item: DownloadItem) {
        try {
            stopAudio()

            val validFile = AudioDownloader.ensureValidAudioFile(context, item)

            val mp = MediaPlayer()
            mediaPlayer = mp

            mp.apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setDataSource(validFile.absolutePath)

                setOnPreparedListener { preparedMp ->
                    if (mediaPlayer === preparedMp) {
                        preparedMp.start()
                        setSpeed(_playerState.value.playbackSpeed)
                        _playerState.value = _playerState.value.copy(
                            currentItem = item,
                            isPlaying = true,
                            isPaused = false,
                            durationMs = preparedMp.duration.coerceAtLeast(1)
                        )
                        startProgressTracker()
                    }
                }

                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        isPaused = false,
                        currentPositionMs = 0
                    )
                    progressJob?.cancel()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stopAudio()
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback", e)
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        val current = _playerState.value
        if (mp.isPlaying) {
            mp.pause()
            _playerState.value = current.copy(isPlaying = false, isPaused = true)
        } else {
            mp.start()
            _playerState.value = current.copy(isPlaying = true, isPaused = false)
            startProgressTracker()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { mp ->
            mp.seekTo(positionMs)
            _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
        }
    }

    fun seekBy(offsetMs: Int) {
        val mp = mediaPlayer ?: return
        val newPos = (mp.currentPosition + offsetMs).coerceIn(0, mp.duration)
        seekTo(newPos)
    }

    fun setSpeed(speed: Float) {
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        mp.playbackParams = PlaybackParams().apply { this.speed = speed }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set playback speed: ${e.message}")
                }
            }
        }
    }

    fun toggleLoop() {
        val newLoop = !_playerState.value.isLooping
        mediaPlayer?.isLooping = newLoop
        _playerState.value = _playerState.value.copy(isLooping = newLoop)
    }

    fun stopAudio() {
        progressJob?.cancel()
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        mediaPlayer = null
        _playerState.value = AudioPlayerState(playbackSpeed = _playerState.value.playbackSpeed)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            currentPositionMs = mp.currentPosition,
                            durationMs = mp.duration.coerceAtLeast(1)
                        )
                    }
                }
                delay(200)
            }
        }
    }

    fun release() {
        stopAudio()
    }
}
