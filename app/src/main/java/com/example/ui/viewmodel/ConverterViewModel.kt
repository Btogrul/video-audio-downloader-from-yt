package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.repository.DownloadRepository
import com.example.service.AudioDownloader
import com.example.service.AudioPlayerManager
import com.example.service.AudioPlayerState
import com.example.service.PresetVideos
import com.example.service.VideoMetadata
import com.example.service.YoutubeExtractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter {
    ALL,
    COMPLETED,
    FAVORITES
}

data class ConverterUiState(
    val urlInput: String = "",
    val selectedFormat: String = "MP3",
    val selectedBitrate: String = "320 kbps",
    val isFetchingPreview: Boolean = false,
    val previewMetadata: VideoMetadata? = null,
    val previewError: String? = null,
    val isConverting: Boolean = false,
    val currentConversionProgress: Float = 0f,
    val currentConversionStatus: String = "",
    val activeConvertingItem: DownloadItem? = null,
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,
    val searchQuery: String = "",
    val selectedDetailsItem: DownloadItem? = null
)

class ConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DownloadRepository
    val playerManager: AudioPlayerManager = AudioPlayerManager(application.applicationContext)

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val playerState: StateFlow<AudioPlayerState> = playerManager.playerState

    private var activeJob: Job? = null
    private var previewJob: Job? = null

    init {
        val database = AppDatabase.getInstance(application)
        repository = DownloadRepository(database.downloadDao())
    }

    val downloadsList: StateFlow<List<DownloadItem>> = combine(
        repository.allDownloads,
        _uiState
    ) { items, state ->
        val filteredByTab = when (state.libraryFilter) {
            LibraryFilter.ALL -> items
            LibraryFilter.COMPLETED -> items.filter { it.status == DownloadStatus.COMPLETED }
            LibraryFilter.FAVORITES -> items.filter { it.isFavorite }
        }

        if (state.searchQuery.isBlank()) {
            filteredByTab
        } else {
            val query = state.searchQuery.trim().lowercase()
            filteredByTab.filter {
                it.title.lowercase().contains(query) ||
                it.author.lowercase().contains(query) ||
                it.format.lowercase().contains(query)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onUrlInputChanged(newUrl: String) {
        val normalized = YoutubeExtractor.normalizeYouTubeUrl(newUrl)
        _uiState.value = _uiState.value.copy(
            urlInput = normalized,
            previewError = null
        )

        // Automatically trigger metadata preview if valid video ID found
        val videoId = YoutubeExtractor.extractVideoId(normalized)
        if (videoId != null && videoId != _uiState.value.previewMetadata?.videoId) {
            fetchPreview(normalized)
        } else if (normalized.isBlank()) {
            _uiState.value = _uiState.value.copy(previewMetadata = null, previewError = null)
        }
    }

    fun pasteFromClipboard(text: String) {
        if (text.isNotBlank()) {
            onUrlInputChanged(text.trim())
            viewModelScope.launch {
                _toastEvent.emit("Pasted YouTube link from clipboard")
            }
        }
    }

    fun clearUrl() {
        _uiState.value = _uiState.value.copy(
            urlInput = "",
            previewMetadata = null,
            previewError = null
        )
    }

    fun selectPreset(preset: VideoMetadata) {
        _uiState.value = _uiState.value.copy(
            urlInput = preset.rawUrl,
            previewMetadata = preset,
            previewError = null
        )
    }

    fun setFormat(format: String) {
        _uiState.value = _uiState.value.copy(selectedFormat = format)
    }

    fun setBitrate(bitrate: String) {
        _uiState.value = _uiState.value.copy(selectedBitrate = bitrate)
    }

    fun setFilter(filter: LibraryFilter) {
        _uiState.value = _uiState.value.copy(libraryFilter = filter)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun showItemDetails(item: DownloadItem?) {
        _uiState.value = _uiState.value.copy(selectedDetailsItem = item)
    }

    fun fetchPreview(url: String) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetchingPreview = true, previewError = null)
            val result = YoutubeExtractor.fetchMetadata(url)
            result.onSuccess { metadata ->
                _uiState.value = _uiState.value.copy(
                    isFetchingPreview = false,
                    previewMetadata = metadata,
                    previewError = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isFetchingPreview = false,
                    previewError = error.message ?: "Failed to fetch video details"
                )
            }
        }
    }

    fun startConversionAndDownload(context: Context) {
        val state = _uiState.value
        val url = state.urlInput.trim()

        if (url.isEmpty()) {
            viewModelScope.launch {
                _toastEvent.emit("Please enter or paste a YouTube URL first")
            }
            return
        }

        val videoId = YoutubeExtractor.extractVideoId(url)
        if (videoId == null) {
            viewModelScope.launch {
                _toastEvent.emit("Invalid YouTube URL. Please check the link.")
            }
            return
        }

        if (state.isConverting) {
            viewModelScope.launch {
                _toastEvent.emit("Conversion already in progress...")
            }
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val metadata = state.previewMetadata ?: VideoMetadata(
                videoId = videoId,
                title = "YouTube Audio ($videoId)",
                author = "YouTube Audio",
                durationSeconds = 180,
                thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                rawUrl = url
            )

            val initialItem = DownloadItem(
                youtubeUrl = metadata.rawUrl,
                videoId = metadata.videoId,
                title = metadata.title,
                author = metadata.author,
                durationSeconds = metadata.durationSeconds,
                thumbnailUrl = metadata.thumbnailUrl,
                format = state.selectedFormat,
                bitrate = state.selectedBitrate,
                status = DownloadStatus.CONVERTING,
                progress = 0.05f,
                statusMessage = "Starting conversion..."
            )

            val itemId = repository.insertOrUpdate(initialItem)
            var currentItem = initialItem.copy(id = itemId)

            _uiState.value = _uiState.value.copy(
                isConverting = true,
                currentConversionProgress = 0.05f,
                currentConversionStatus = "Starting conversion...",
                activeConvertingItem = currentItem
            )

            val downloadResult = AudioDownloader.convertAndDownload(
                context = context,
                item = currentItem,
                onProgress = { progress, message ->
                    _uiState.value = _uiState.value.copy(
                        currentConversionProgress = progress,
                        currentConversionStatus = message
                    )
                    currentItem = currentItem.copy(
                        progress = progress,
                        statusMessage = message
                    )
                    repository.update(currentItem)
                }
            )

            downloadResult.onSuccess { result ->
                val completedItem = currentItem.copy(
                    filePath = result.filePath,
                    contentUri = result.contentUri,
                    fileSizeBytes = result.fileSizeBytes,
                    status = DownloadStatus.COMPLETED,
                    progress = 1.0f,
                    statusMessage = "Downloaded directly to device storage"
                )
                repository.update(completedItem)

                _uiState.value = _uiState.value.copy(
                    isConverting = false,
                    currentConversionProgress = 1.0f,
                    currentConversionStatus = "Complete!",
                    activeConvertingItem = null
                )

                _toastEvent.emit("Successfully converted! ${completedItem.format} saved to Music / Downloads")
            }.onFailure { error ->
                val failedItem = currentItem.copy(
                    status = DownloadStatus.FAILED,
                    progress = 0f,
                    statusMessage = error.message ?: "Conversion failed"
                )
                repository.update(failedItem)

                _uiState.value = _uiState.value.copy(
                    isConverting = false,
                    currentConversionProgress = 0f,
                    currentConversionStatus = "Conversion failed",
                    activeConvertingItem = null
                )

                _toastEvent.emit("Error: ${error.message ?: "Failed to convert video"}")
            }
        }
    }

    fun playAudio(item: DownloadItem) {
        playerManager.playAudio(item)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Int) {
        playerManager.seekTo(positionMs)
    }

    fun seekBy(offsetMs: Int) {
        playerManager.seekBy(offsetMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    fun toggleLoop() {
        playerManager.toggleLoop()
    }

    fun toggleFavorite(item: DownloadItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, item.isFavorite)
        }
    }

    fun deleteDownload(item: DownloadItem) {
        viewModelScope.launch {
            if (playerState.value.currentItem?.id == item.id) {
                playerManager.stopAudio()
            }
            repository.delete(item)
            _toastEvent.emit("Removed from library")
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            playerManager.stopAudio()
            repository.clearAll()
            _toastEvent.emit("Library cleared")
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
