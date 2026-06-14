package by.w6.my1drive.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import by.w6.my1drive.data.local.MediaDao
import by.w6.my1drive.data.local.MediaEntity
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepositoryImpl(
    private val context: Context,
    private val mediaDao: MediaDao
) : MediaRepository {

    private val refreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0L)

    override fun getMediaItemsFlow(): Flow<List<MediaItem>> {
        val archivedFlow = mediaDao.getAllFlow()

        return combine(refreshTrigger, archivedFlow) { _, archivedEntities ->
            val localList = queryLocalMediaStore()
            val archivedItems = archivedEntities.map { entity ->
                val thumbnailUri = entity.thumbnailPath?.let { path ->
                    Uri.fromFile(File(path))
                } ?: Uri.EMPTY

                MediaItem(
                    id = "archived_${entity.id}",
                    displayName = entity.displayName,
                    uri = entity.thumbnailPath?.let { Uri.fromFile(File(it)) } ?: Uri.EMPTY,
                    mimeType = entity.mimeType,
                    size = entity.size,
                    dateModified = entity.dateModified,
                    status = MediaStatus.ARCHIVED_OTG,
                    duration = entity.duration,
                    hash = entity.id,
                    otgUri = entity.otgUri,
                    thumbnailPath = entity.thumbnailPath,
                    originalRelativePath = entity.originalRelativePath
                )
            }

            // Quick heuristic to filter out duplicates (local files already in database)
            val archivedKeys = archivedEntities.map { it.displayName to it.size }.toSet()
            val filteredLocalList = localList.filterNot { localItem ->
                archivedKeys.contains(localItem.displayName to localItem.size)
            }

            (filteredLocalList + archivedItems).sortedByDescending { it.dateModified }
        }.flowOn(Dispatchers.IO)
    }

    override fun refresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    override suspend fun insertArchivedItem(
        item: MediaItem,
        otgUri: String,
        hash: String,
        thumbnailPath: String?,
        originalRelativePath: String?
) = withContext(Dispatchers.IO) {
        val entity = MediaEntity(
            id = hash,
            displayName = item.displayName,
            mimeType = item.mimeType,
            size = item.size,
            dateModified = item.dateModified,
            otgUri = otgUri,
            thumbnailPath = thumbnailPath,
            duration = item.duration,
            originalRelativePath = originalRelativePath ?: item.originalRelativePath
        )
        mediaDao.insert(entity)
    }

    override suspend fun deleteArchivedItem(item: MediaItem) = withContext(Dispatchers.IO) {
        val hash = item.hash ?: return@withContext
        item.thumbnailPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        val entity = mediaDao.getById(hash)
        if (entity != null) {
            mediaDao.delete(entity)
        }
    }

    override suspend fun clearAllArchivedItems() = withContext(Dispatchers.IO) {
        // Clear old thumbnails dir
        val thumbDir = File(context.filesDir, "thumbnails")
        thumbDir.listFiles()?.forEach { it.delete() }
        // Clear new preview cache dir
        val previewDir = File(context.filesDir, "my1drive_previews")
        previewDir.listFiles()?.forEach { it.delete() }
        mediaDao.deleteAll()
    }

    private fun queryLocalMediaStore(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val contentResolver = context.contentResolver

        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to true,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to false
        )

        for ((collection, isImage) in collections) {
            val projection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            if (!isImage) {
                projection.add(MediaStore.Video.VideoColumns.DURATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection.add(MediaStore.MediaColumns.IS_PENDING)
                projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                projection.add(MediaStore.MediaColumns.IS_TRASHED)
            }

            val query = contentResolver.query(
                collection,
                projection.toTypedArray(),
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )

            query?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val durationColumn = if (!isImage) {
                    cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                } else -1
                val isPendingColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                } else -1
                val isTrashedColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                } else -1
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    if (isPendingColumn != -1 && cursor.getInt(isPendingColumn) != 0) {
                        continue
                    }
                    if (isTrashedColumn != -1 && cursor.getInt(isTrashedColumn) != 0) {
                        continue
                    }

                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Unnamed"
                    val mimeType = cursor.getString(mimeColumn) ?: (if (isImage) "image/jpeg" else "video/mp4")
                    val size = cursor.getLong(sizeColumn)
                    val dateModified = cursor.getLong(dateColumn)
                    val duration = if (!isImage && durationColumn != -1) {
                        cursor.getLong(durationColumn)
                    } else null
                    val relativePath = if (relativePathColumn != -1) {
                        cursor.getString(relativePathColumn)
                    } else null

                    val contentUri = ContentUris.withAppendedId(collection, id)

                    list.add(
                        MediaItem(
                            id = "local_$id",
                            displayName = name,
                            uri = contentUri,
                            mimeType = mimeType,
                            size = size,
                            dateModified = dateModified,
                            status = MediaStatus.ON_DEVICE,
                            duration = duration,
                            originalRelativePath = relativePath
                        )
                    )
                }
            }
        }

        return list.sortedByDescending { it.dateModified }
    }
}




