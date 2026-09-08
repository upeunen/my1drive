package by.w6.my1drive.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import by.w6.my1drive.data.local.MediaDao
import by.w6.my1drive.data.local.MediaEntity
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MediaRepositoryImpl(
    private val context: Context,
    private val mediaDao: MediaDao
) : MediaRepository {

    private val _localItemsCache = kotlinx.coroutines.flow.MutableStateFlow<List<MediaItem>>(emptyList())
    private val aspectRatioCache = ConcurrentHashMap<String, Float>()
    
    init {
        GlobalScope.launch(Dispatchers.IO) {
            _localItemsCache.value = queryLocalMediaStore()
        }
    }

    private fun getPrefsFlow(): Flow<Pair<Boolean, String>> = flow {
        val prefs = context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE)
        val channel = kotlinx.coroutines.channels.Channel<Pair<Boolean, String>>(kotlinx.coroutines.channels.Channel.CONFLATED)

        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "show_offline_archives" || key == "active_archive_uuid") {
                val showOffline = sp.getBoolean("show_offline_archives", false)
                val activeUuid = sp.getString("active_archive_uuid", "") ?: ""
                channel.trySend(Pair(showOffline, activeUuid))
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        val initialShowOffline = prefs.getBoolean("show_offline_archives", false)
        val initialActiveUuid = prefs.getString("active_archive_uuid", "") ?: ""
        emit(Pair(initialShowOffline, initialActiveUuid))

        try {
            for (value in channel) {
                emit(value)
            }
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override fun getMediaItemsFlow(): Flow<List<MediaItem>> {
        val archivedFlow = mediaDao.getAllFlow()
        val archivesFlow = by.w6.my1drive.data.local.AppDatabase.getDatabase(context).archiveDao().getAllFlow()
        val prefsFlow = getPrefsFlow()

        return combine(_localItemsCache, archivedFlow, archivesFlow, prefsFlow) { localList, archivedEntities, archives, prefsPair ->
            val archiveNamesMap = archives.associate { it.uuid to it.name }
            val showOffline = prefsPair.first
            val activeUuid = prefsPair.second

            val filteredEntities = if (showOffline) {
                archivedEntities
            } else {
                archivedEntities.filter { it.archiveUuid == activeUuid }
            }

            val archivedItems = filteredEntities.map { entity ->
                var effectiveRatio = if (entity.width > 0 && entity.height > 0) {
                    entity.width.toFloat() / entity.height.toFloat()
                } else {
                    aspectRatioCache[entity.id] ?: 0f
                }

                if (effectiveRatio <= 0f && entity.thumbnailPath != null) {
                    val previewFile = File(entity.thumbnailPath)
                    if (previewFile.exists()) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(entity.thumbnailPath, opts)
                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                            effectiveRatio = opts.outWidth.toFloat() / opts.outHeight.toFloat()
                            aspectRatioCache[entity.id] = effectiveRatio
                        }
                    }
                }

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
                    originalRelativePath = entity.originalRelativePath,
                    dateArchived = entity.dateArchived,
                    dateAdded = null,
                    archiveUuid = entity.archiveUuid,
                    archiveName = archiveNamesMap[entity.archiveUuid] ?: context.getString(by.w6.my1drive.R.string.repository_unknown_drive),
                    aspectRatio = effectiveRatio
                )
            }

            val archivedKeys = filteredEntities.map { it.displayName to it.size }.toSet()
            val filteredLocalList = localList.filterNot { localItem ->
                archivedKeys.contains(localItem.displayName to localItem.size)
            }

            (filteredLocalList + archivedItems).sortedByDescending { it.dateModified }
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    override fun refresh() {
        GlobalScope.launch(Dispatchers.IO) {
            _localItemsCache.value = queryLocalMediaStore()
        }
    }

    override suspend fun insertArchivedItem(
        item: MediaItem,
        otgUri: String,
        hash: String,
        thumbnailPath: String?,
        originalRelativePath: String?,
        dateArchived: Long
    ) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE)
        val activeUuid = prefs.getString("active_archive_uuid", "") ?: ""
        val entity = MediaEntity(
            id = hash,
            displayName = item.displayName,
            mimeType = item.mimeType,
            size = item.size,
            dateModified = item.dateModified,
            otgUri = otgUri,
            thumbnailPath = thumbnailPath,
            duration = item.duration,
            originalRelativePath = originalRelativePath ?: item.originalRelativePath,
            dateArchived = dateArchived,
            archiveUuid = activeUuid,
            width = if (item.aspectRatio > 0f) (item.aspectRatio * 1000).toInt() else 0,
            height = if (item.aspectRatio > 0f) 1000 else 0
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
        val thumbDir = File(context.filesDir, "thumbnails")
        thumbDir.listFiles()?.forEach { it.delete() }
        val previewDir = File(context.filesDir, "my1drive_previews")
        previewDir.listFiles()?.forEach { it.delete() }
        mediaDao.deleteAll()
    }

    private fun queryLocalMediaStore(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
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
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT
                )
                if (!isImage) {
                    projection.add(MediaStore.Video.VideoColumns.DURATION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    projection.add(MediaStore.MediaColumns.ORIENTATION)
                    projection.add(MediaStore.MediaColumns.IS_PENDING)
                    projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
                } else {
                    if (isImage) {
                        projection.add(MediaStore.Images.ImageColumns.ORIENTATION)
                    }
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
                    val orientationColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
                    } else {
                        cursor.getColumnIndex("orientation")
                    }

                    val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)

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
                        val dateAdded = cursor.getLong(addedColumn)
                        val duration = if (!isImage && durationColumn != -1) {
                            cursor.getLong(durationColumn)
                        } else null
                        val relativePath = if (relativePathColumn != -1) {
                            cursor.getString(relativePathColumn)
                        } else null

                        val rawWidth = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                        val rawHeight = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
                        val orientation = if (orientationColumn != -1) cursor.getInt(orientationColumn) else 0

                        val isRotated = orientation == 90 || orientation == 270
                        val width = if (isRotated) rawHeight else rawWidth
                        val height = if (isRotated) rawWidth else rawHeight

                        val aspectRatio = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 0f

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
                                originalRelativePath = relativePath,
                                dateAdded = dateAdded,
                                aspectRatio = aspectRatio
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list.sortedByDescending { it.dateModified }
    }
}
