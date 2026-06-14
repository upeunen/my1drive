package by.w6.my1drive.domain.repository

import by.w6.my1drive.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getMediaItemsFlow(): Flow<List<MediaItem>>
    suspend fun insertArchivedItem(item: MediaItem, otgUri: String, hash: String, thumbnailPath: String?, originalRelativePath: String?)
    suspend fun deleteArchivedItem(item: MediaItem)
    fun refresh()
    suspend fun clearAllArchivedItems()
}

