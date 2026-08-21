package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    IDLE,
    FETCHING_INFO,
    CONVERTING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

@Entity(tableName = "download_items")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val youtubeUrl: String,
    val videoId: String,
    val title: String,
    val author: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String = "",
    val format: String = "MP3",
    val bitrate: String = "320 kbps",
    val fileSizeBytes: Long = 0L,
    val filePath: String = "",
    val contentUri: String? = null,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val downloadedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    val formattedFileSize: String
        get() {
            if (fileSizeBytes <= 0) return "Unknown size"
            val mb = fileSizeBytes.toDouble() / (1024 * 1024)
            return String.format("%.2f MB", mb)
        }
}
