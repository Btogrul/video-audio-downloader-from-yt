package com.example.data.repository

import com.example.data.local.DownloadDao
import com.example.data.model.DownloadItem
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadItem>> = downloadDao.getCompletedDownloads()
    val favoriteDownloads: Flow<List<DownloadItem>> = downloadDao.getFavoriteDownloads()

    suspend fun getDownloadById(id: Long): DownloadItem? = downloadDao.getDownloadById(id)

    suspend fun getDownloadByVideoId(videoId: String): DownloadItem? = downloadDao.getDownloadByVideoId(videoId)

    suspend fun insertOrUpdate(item: DownloadItem): Long = downloadDao.insert(item)

    suspend fun update(item: DownloadItem) = downloadDao.update(item)

    suspend fun delete(item: DownloadItem) = downloadDao.delete(item)

    suspend fun deleteById(id: Long) = downloadDao.deleteById(id)

    suspend fun toggleFavorite(id: Long, currentStatus: Boolean) {
        downloadDao.updateFavorite(id, !currentStatus)
    }

    suspend fun clearAll() = downloadDao.clearAll()
}
