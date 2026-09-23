package by.w6.my1drive.ui

import android.app.Application
import android.net.Uri
import java.io.File
import androidx.documentfile.provider.DocumentFile
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import by.w6.my1drive.data.local.AppDatabase
import by.w6.my1drive.data.local.MediaEntity
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.repository.MediaRepository
import by.w6.my1drive.utils.ArchiveMetadataStore
import by.w6.my1drive.utils.CopyVerifyResult
import by.w6.my1drive.utils.DebugLogBuffer
import by.w6.my1drive.utils.JsonEntry
import by.w6.my1drive.utils.OtgArchiveUtil
import by.w6.my1drive.utils.PreviewCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import by.w6.my1drive.utils.VpsConnectionManager


/** Helper for archive/manual-sync operations extracted from GalleryViewModel */
class ArchiveSyncHelper private constructor(
    private val application: Application,
    private val db: AppDatabase,
    private val repository: MediaRepository,
    private val archiveUtil: OtgArchiveUtil,
    private val prefs: android.content.SharedPreferences,
    private val previewCache: PreviewCacheManager
) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    val operationCompleteEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val archiveSuccessEvent = kotlinx.coroutines.flow.MutableSharedFlow<List<MediaItem>>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val itemArchivedEvent = kotlinx.coroutines.flow.MutableSharedFlow<MediaItem>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val previewCachedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    companion object {
        @Volatile
        private var INSTANCE: ArchiveSyncHelper? = null

        fun getInstance(application: Application): ArchiveSyncHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildInstance(application).also { INSTANCE = it }
            }
        }

        private fun buildInstance(application: Application): ArchiveSyncHelper {
            val db = AppDatabase.getDatabase(application)
            val repository = by.w6.my1drive.data.repository.MediaRepositoryImpl(application, db.mediaDao())
            val archiveUtil = OtgArchiveUtil(application)
            val prefs = application.getSharedPreferences("my1drive_prefs", android.content.Context.MODE_PRIVATE)
            val previewCache = PreviewCacheManager(application, db.mediaDao())
            return ArchiveSyncHelper(application, db, repository, archiveUtil, prefs, previewCache)
        }
        private val operationMutex = Mutex()
        
        data class FastDocumentFile(
            val uri: android.net.Uri,
            val name: String,
            val length: Long,
            val mimeType: String,
            val lastModified: Long,
            val relativePath: String = ""
        )
        
        fun fastListFiles(context: android.content.Context, dirUri: android.net.Uri, isCancelled: () -> Boolean = { false }): List<FastDocumentFile> {
            val results = mutableListOf<FastDocumentFile>()
            try {
                val rootDocId = try {
                    android.provider.DocumentsContract.getDocumentId(dirUri)
                } catch (e: Exception) {
                    android.provider.DocumentsContract.getTreeDocumentId(dirUri)
                }
                val projection = arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_SIZE,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )

                val queue = java.util.ArrayDeque<Pair<String, String>>()
                queue.add(rootDocId to "")

                while (queue.isNotEmpty()) {
                    if (isCancelled()) break
                    val (currentDocId, relPrefix) = queue.poll() ?: break

                    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                        dirUri, currentDocId
                    )

                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                        val mimeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val modIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                        while (cursor.moveToNext()) {
                            if (isCancelled()) break
                            val docId = cursor.getString(idIdx) ?: continue
                            val mime = cursor.getString(mimeIdx) ?: ""
                            val name = cursor.getString(nameIdx) ?: continue

                            if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                                // Игнорируем только системные корзины и служебную папку превью My1drive
                                if (name.equals(".previews", ignoreCase = true) ||
                                    name.equals("\$RECYCLE.BIN", ignoreCase = true) ||
                                    name.equals("System Volume Information", ignoreCase = true) ||
                                    name.equals("LOST.DIR", ignoreCase = true)
                                ) {
                                    continue
                                }
                                // Папки с .nomedia обязательно сканируем
                                queue.add(docId to "$relPrefix$name/")
                                continue
                            }

                            // Для файлов: не добавляем в медиатеку маркер .nomedia и служебные файлы
                            if (name.endsWith(".tmp") ||
                                name.equals(".nomedia", ignoreCase = true) ||
                                name == ".my1drive_uuid" || name == ".my1drive_uuid.txt" || 
                                name == ".my1drive_db.json" || name == "my1drive_db.json"
                            ) {
                                continue
                            }

                            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(dirUri, docId)
                            val size = cursor.getLong(sizeIdx)
                            val modified = cursor.getLong(modIdx)

                            results.add(FastDocumentFile(docUri, name, size, mime, modified, "$relPrefix$name"))
                        }
                    }
                }
            } catch (e: Exception) {
                by.w6.my1drive.utils.DebugLogBuffer.log("ArchiveSyncHelper", "fastListFiles error: ${e.message}")
            }
            return results
        }
    }

    private val metadataStore = ArchiveMetadataStore(application)
    private val vpsManager = VpsConnectionManager(application)

    private val _syncState = MutableStateFlow<by.w6.my1drive.utils.UiText?>(null)
    val syncState: StateFlow<by.w6.my1drive.utils.UiText?> = _syncState.asStateFlow()

    private val _archiveState = MutableStateFlow(ArchiveState())
        val archiveState: StateFlow<ArchiveState> = _archiveState.asStateFlow()

    private val _missingFilesNotification = MutableStateFlow<List<String>?>(null)
    val missingFilesNotification: StateFlow<List<String>?> = _missingFilesNotification.asStateFlow()

    private val _autoSyncAddedCount = MutableStateFlow(0)
    val autoSyncAddedCount: StateFlow<Int> = _autoSyncAddedCount.asStateFlow()

    private val _syncProgressState = MutableStateFlow(SyncProgressState())
    val syncProgressState: StateFlow<SyncProgressState> = _syncProgressState.asStateFlow()

    private val _archivingItemIds = MutableStateFlow<Set<String>>(emptySet())
    val archivingItemIds: StateFlow<Set<String>> = _archivingItemIds.asStateFlow()

    private val _copiedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val copiedItemIds: StateFlow<Set<String>> = _copiedItemIds.asStateFlow()

    private val _isSilentSyncing = MutableStateFlow(false)
    val isSilentSyncingFlow: StateFlow<Boolean> = _isSilentSyncing.asStateFlow()
    var isSilentSyncing: Boolean
        get() = _isSilentSyncing.value
        set(value) { _isSilentSyncing.value = value }

    private val PREF_MISSING_FILES_DISMISSED = "missing_files_dismissed"
    private val PREF_MISSING_FILES_HASH = "missing_files_hash"

    // ─── Silent auto-sync ───

    private var activeSyncJob: Job? = null

    /**
     * Отменяет текущую синхронизацию и архивирование без ожидания.
     */
    fun cancelOperations() {
        isCancellationRequested = true
        isSilentSyncing = false
        archiveQueue.clear()
        activeArchiveJob?.cancel()
        activeArchiveJob = null
        isArchiveJobRunning = false
        activeSyncJob?.cancel()
        activeSyncJob = null

        _syncProgressState.value = SyncProgressState(isSyncing = false)
        _archivingItemIds.value = emptySet()
        _archiveState.value = ArchiveState(isArchiving = false)
        by.w6.my1drive.utils.DebugLogBuffer.log("ArchiveSyncHelper", "cancelOperations: sync and archive jobs cancelled")
    }

    /**
     * Гарантированно отменяет и ожидает фактического завершения всех фоновых операций с OTG (синхронизация и архивирование).
     */
    suspend fun stopAllOperations() {
        by.w6.my1drive.utils.DebugLogBuffer.log("ArchiveSyncHelper", "stopAllOperations: stopping sync and archiving jobs...")
        isCancellationRequested = true
        isSilentSyncing = false

        try {
            // 1. Отменяем архивацию и очищаем очередь
            archiveQueue.clear()
            val archiveJobToWait = activeArchiveJob
            archiveJobToWait?.cancel()

            // 2. Отменяем синхронизацию
            val syncJobToWait = activeSyncJob
            syncJobToWait?.cancel()

            // 3. Дожидаемся фактического завершения корутин (до 4 секунд)
            withTimeoutOrNull(4000L) {
                archiveJobToWait?.join()
                syncJobToWait?.join()
            }

            activeArchiveJob = null
            activeSyncJob = null
            isArchiveJobRunning = false

            // 4. Сбрасываем стейты
            _syncProgressState.value = SyncProgressState(isSyncing = false)
            _copiedItemIds.value = emptySet()
            _archivingItemIds.value = emptySet()
            _archiveState.value = ArchiveState(isArchiving = false)

            by.w6.my1drive.utils.DebugLogBuffer.log("ArchiveSyncHelper", "stopAllOperations: all jobs stopped and joined")
        } finally {
            isCancellationRequested = false
        }
    }

    fun silentSyncArchive(otgDirectoryUri: Uri?, targetArchiveUuid: String? = null) {
        val uri = otgDirectoryUri ?: return
        
        // Отменяем предыдущую синхронизацию, если она есть
        activeSyncJob?.cancel()
        isCancellationRequested = false
        
        isSilentSyncing = true
        val logTag = "SilentSync"
        activeSyncJob = scope.launch(Dispatchers.IO) {
            try {
                operationMutex.withLock {
                    try {
                        DebugLogBuffer.log(logTag, "Start silentSyncArchive: targetUri=$uri, targetUuid=$targetArchiveUuid")

                        val activeUuidsToSync = if (!targetArchiveUuid.isNullOrEmpty()) {
                            listOf(targetArchiveUuid)
                        } else {
                            val recovered = by.w6.my1drive.utils.OtgFolderResolver.scanAndRecoverAllArchives(application, uri, autoInsertToDb = false)
                            val list = recovered.map { it.uuid }.toMutableList()
                            val primaryActive = prefs.getString("active_archive_uuid", null)
                            if (primaryActive != null && primaryActive in list) {
                                list.remove(primaryActive)
                                list.add(0, primaryActive)
                            }
                            if (list.isEmpty()) {
                                val fallback = by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(uri) ?: uri.toString().hashCode().toString()
                                list.add(fallback)
                            }
                            list
                        }

                        for (uuid in activeUuidsToSync) {
                            if (isCancellationRequested || !isActive) break
                            syncSingleArchiveSilently(uri, uuid, logTag)
                        }

                        withContext(Dispatchers.Main) {
                            repository.refresh()
                        }
                        DebugLogBuffer.log(logTag, "Silent sync finished successfully for: $activeUuidsToSync")

                        // Очищаем осиротевшие превью из кэша (для файлов, которых больше нет на флешке)
                        previewCache.cleanupOrphanedPreviews(null)

                    } catch (e: Exception) {
                        DebugLogBuffer.log(logTag, "Error in silentSyncArchive: ${e.localizedMessage}")
                        val sw = java.io.StringWriter()
                        e.printStackTrace(java.io.PrintWriter(sw))
                        DebugLogBuffer.log(logTag, "Stacktrace: $sw")
                    }
                }
            } finally {
                isSilentSyncing = false
                operationCompleteEvent.tryEmit(Unit)
            }
        }
    }

    private suspend fun syncSingleArchiveSilently(uri: Uri, activeUuid: String, logTag: String) {
        val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(
            application, uri, createIfNotExist = false, targetArchiveUuid = activeUuid
        )
        DebugLogBuffer.log(logTag, "syncSingleArchiveSilently: activeUuid=$activeUuid, dir=${dir?.name}")

        // ── Шаг 1: Чтение JSON метаданных (источник истины) ──
        val jsonEntries = metadataStore.readMetadata(uri, targetArchiveUuid = activeUuid)
        DebugLogBuffer.log(logTag, "Read metadata: ${jsonEntries.size} JSON entries for $activeUuid")

        // ── Фаза 1 (МГНОВЕННАЯ): Показываем файлы из JSON в галерее сразу (до 0.5 сек) ──
        if (jsonEntries.isNotEmpty()) {
            val initialRoomEntities = db.mediaDao().getByArchiveUuidInChunksSync(activeUuid)
            val initialRoomMap = initialRoomEntities.associateBy { it.id }
            val initialBatch = mutableListOf<MediaEntity>()

            for (entry in jsonEntries) {
                if (isCancellationRequested) break
                val existing = initialRoomMap[entry.hash]
                val fallbackUri = if (dir != null) {
                    by.w6.my1drive.utils.OtgFolderResolver.buildDirectChildUri(dir.uri, entry.displayName).toString()
                } else ""

                val localPreview = previewCache.cacheFileFor(entry.hash)
                val resolvedThumb = if (localPreview.exists() && localPreview.length() > 0) localPreview.absolutePath else null

                if (existing == null) {
                    initialBatch.add(MediaEntity(
                        id = entry.hash,
                        displayName = entry.displayName,
                        mimeType = entry.mimeType,
                        size = entry.size,
                        dateModified = entry.dateModified,
                        otgUri = fallbackUri,
                        thumbnailPath = resolvedThumb,
                        duration = entry.duration,
                        originalRelativePath = entry.originalRelativePath,
                        dateArchived = entry.dateArchived,
                        archiveUuid = activeUuid,
                        width = entry.width,
                        height = entry.height
                    ))
                } else {
                    val actualThumb = if (!existing.thumbnailPath.isNullOrEmpty() && java.io.File(existing.thumbnailPath).exists()) {
                        existing.thumbnailPath
                    } else resolvedThumb

                    if (existing.displayName != entry.displayName || 
                        existing.size != entry.size || 
                        existing.dateModified != entry.dateModified ||
                        existing.archiveUuid != activeUuid ||
                        existing.thumbnailPath != actualThumb ||
                        (existing.width == 0 && entry.width > 0) ||
                        (existing.height == 0 && entry.height > 0)
                    ) {
                        initialBatch.add(existing.copy(
                            displayName = entry.displayName,
                            mimeType = entry.mimeType,
                            size = entry.size,
                            dateModified = entry.dateModified,
                            thumbnailPath = actualThumb,
                            duration = entry.duration,
                            originalRelativePath = entry.originalRelativePath,
                            dateArchived = entry.dateArchived,
                            archiveUuid = activeUuid,
                            width = if (existing.width > 0) existing.width else entry.width,
                            height = if (existing.height > 0) existing.height else entry.height
                        ))
                    }
                }

                if (initialBatch.size >= 500) {
                    db.mediaDao().insertAll(initialBatch)
                    initialBatch.clear()
                }
            }

            if (initialBatch.isNotEmpty()) {
                db.mediaDao().insertAll(initialBatch)
                initialBatch.clear()
            }

            withContext(Dispatchers.Main) {
                repository.refresh()
            }
            DebugLogBuffer.log(logTag, "Phase 1 instant UI refresh complete with ${jsonEntries.size} items for $activeUuid")
        }

        // ── Фаза 2 (ФОНОВАЯ): Сканирование физической папки на флешке, дедупликация и очистка ──
        val metadataExists = metadataStore.metadataExists(uri, targetArchiveUuid = activeUuid)
        val physicalFiles = if (dir != null && dir.exists()) {
            fastListFiles(application, dir.uri) { isCancellationRequested }
        } else emptyList()

        DebugLogBuffer.log(logTag, "Metadata exists: $metadataExists. Physical files found: ${physicalFiles.size} for $activeUuid")

        if (jsonEntries.isEmpty() && !metadataExists && physicalFiles.isEmpty()) {
            DebugLogBuffer.log(logTag, "No metadata file and no physical files found on OTG. Skipping Room database sync to preserve cache.")
            return
        }

        val validJsonEntries = jsonEntries.toMutableList()
        var jsonChanged = false

        if (dir != null && dir.exists()) {
            // HashMap/HashSet с заданной ёмкостью — один проход вместо трёх отдельных копий
            val knownNamesMap = jsonEntries.associateByTo(HashMap(jsonEntries.size * 2)) { it.displayName.lowercase() }
            val knownHashes = jsonEntries.mapTo(HashSet(jsonEntries.size * 2)) { it.hash }

            for (file in physicalFiles) {
                if (isCancellationRequested) {
                    DebugLogBuffer.log(logTag, "Silent sync cancelled during scan loop")
                    break
                }

                val name = file.name
                val entry = knownNamesMap[name.lowercase()]
                if (entry != null) {
                    continue
                }

                DebugLogBuffer.log(logTag, "Scanning detected new physical file: $name. Using name+size as hash...")
                val hash = "${name}_${file.length}"

                if (hash !in knownHashes) {
                    val mime = file.mimeType.ifEmpty { "image/jpeg" }
                    val relSubfolder = file.relativePath.substringBeforeLast('/', "")
                    val defaultPath = if (relSubfolder.isNotEmpty()) "$relSubfolder/" else if (mime.startsWith("video/")) "Movies/" else "Pictures/"
                    val dims = if (mime.startsWith("image/")) {
                        try {
                            by.w6.my1drive.utils.ExifHelper.getImageDimensions(application, file.uri, mime)
                        } catch (_: Exception) { 0 to 0 }
                    } else 0 to 0

                    val newEntry = JsonEntry(
                        hash = hash,
                        displayName = name,
                        mimeType = mime,
                        size = file.length,
                        dateModified = file.lastModified / 1000,
                        originalRelativePath = defaultPath,
                        duration = null,
                        dateArchived = System.currentTimeMillis() / 1000,
                        width = dims.first,
                        height = dims.second
                    )
                    validJsonEntries.add(newEntry)
                    knownHashes.add(hash)
                    jsonChanged = true
                    DebugLogBuffer.log(logTag, "Scanned and added new file to metadata: $name (hash=$hash)")
                }
            }

            // Диск — источник истины: если файл физически удален с диска, удаляем из метаданных
            if (physicalFiles.isNotEmpty()) {
                val physicalKeys = physicalFiles.map { it.name.lowercase() to it.length }.toSet()
                val jsonIter = validJsonEntries.iterator()
                var prunedCount = 0
                while (jsonIter.hasNext()) {
                    val entry = jsonIter.next()
                    val key = entry.displayName.lowercase() to entry.size
                    if (key !in physicalKeys) {
                        jsonIter.remove()
                        prunedCount++
                        DebugLogBuffer.log(logTag, "Pruning file missing from disk from metadata: ${entry.displayName}")
                    }
                }
                if (prunedCount > 0) {
                    jsonChanged = true
                    DebugLogBuffer.log(logTag, "Pruned $prunedCount files missing from disk from JSON metadata")
                }
            }
        }

        // Записываем обновленные метаданные на диск только если были изменения
        if (jsonChanged) {
            DebugLogBuffer.log(logTag, "Metadata changed, saving ${validJsonEntries.size} entries back to OTG JSON...")
            metadataStore.writeMetadata(uri, validJsonEntries, targetArchiveUuid = activeUuid)
        }

        // Синхронизируем Room с итоговым валидным списком JSON
        val phase2RoomEntities = db.mediaDao().getByArchiveUuidInChunksSync(activeUuid)
        val phase2RoomMap = phase2RoomEntities.associateBy { it.id }
        val finalHashes = validJsonEntries.map { it.hash }.toHashSet()
        val phase2Batch = mutableListOf<MediaEntity>()
        var insertedToRoom = 0

        for (entry in validJsonEntries) {
            if (isCancellationRequested) break
            val existing = phase2RoomMap[entry.hash]
            val fallbackUri = if (dir != null) {
                by.w6.my1drive.utils.OtgFolderResolver.buildDirectChildUri(dir.uri, entry.displayName).toString()
            } else ""

            val localPreview = previewCache.cacheFileFor(entry.hash)
            val resolvedThumb = if (localPreview.exists() && localPreview.length() > 0) localPreview.absolutePath else null

            if (existing == null) {
                phase2Batch.add(MediaEntity(
                    id = entry.hash,
                    displayName = entry.displayName,
                    mimeType = entry.mimeType,
                    size = entry.size,
                    dateModified = entry.dateModified,
                    otgUri = fallbackUri,
                    thumbnailPath = resolvedThumb,
                    duration = entry.duration,
                    originalRelativePath = entry.originalRelativePath,
                    dateArchived = entry.dateArchived,
                    archiveUuid = activeUuid,
                    width = entry.width,
                    height = entry.height
                ))
                insertedToRoom++
            } else {
                val actualThumb = if (!existing.thumbnailPath.isNullOrEmpty() && java.io.File(existing.thumbnailPath).exists()) {
                    existing.thumbnailPath
                } else resolvedThumb

                if (existing.displayName != entry.displayName || 
                    existing.size != entry.size || 
                    existing.dateModified != entry.dateModified ||
                    existing.archiveUuid != activeUuid ||
                    existing.thumbnailPath != actualThumb ||
                    (existing.width == 0 && entry.width > 0) ||
                    (existing.height == 0 && entry.height > 0)
                ) {
                    phase2Batch.add(existing.copy(
                        displayName = entry.displayName,
                        mimeType = entry.mimeType,
                        size = entry.size,
                        dateModified = entry.dateModified,
                        thumbnailPath = actualThumb,
                        duration = entry.duration,
                        originalRelativePath = entry.originalRelativePath,
                        dateArchived = entry.dateArchived,
                        archiveUuid = activeUuid,
                        width = if (existing.width > 0) existing.width else entry.width,
                        height = if (existing.height > 0) existing.height else entry.height
                    ))
                }
            }

            if (phase2Batch.size >= 500) {
                db.mediaDao().insertAll(phase2Batch)
                phase2Batch.clear()
            }
        }

        if (phase2Batch.isNotEmpty()) {
            db.mediaDao().insertAll(phase2Batch)
            phase2Batch.clear()
        }
        if (insertedToRoom > 0) {
            DebugLogBuffer.log(logTag, "Added $insertedToRoom missing entries from JSON to Room for $activeUuid")
        }

        // Удаляем из Room записи, которых больше нет в JSON
        val deadEntities = mutableListOf<by.w6.my1drive.data.local.MediaEntity>()
        for (entity in phase2RoomEntities) {
            if (entity.id !in finalHashes) {
                entity.thumbnailPath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) file.delete()
                }
                deadEntities.add(entity)
            }
        }
        if (deadEntities.isNotEmpty()) {
            db.mediaDao().deleteEntities(deadEntities)
            DebugLogBuffer.log(logTag, "Removed ${deadEntities.size} dead entries from Room database for $activeUuid")
        }
    }

    fun dismissMissingFilesNotification() {
        val names = _missingFilesNotification.value
        if (names != null) {
            prefs.edit()
                .putBoolean(PREF_MISSING_FILES_DISMISSED, true)
                .putString(PREF_MISSING_FILES_HASH, names.sorted().joinToString(","))
                .apply()
        }
        _missingFilesNotification.value = null
    }


    fun dismissAutoSyncAddedCount() { _autoSyncAddedCount.value = 0 }


    // ─── Manual sync ───

    /**
     * Manual sync: same as silent sync but with progress reporting.
     */
    fun syncArchive(otgDirectoryUri: Uri?) {
        val uri = otgDirectoryUri ?: return
        
        activeSyncJob?.cancel()
        
        activeSyncJob = scope.launch {
            _syncProgressState.value = SyncProgressState(
                isSyncing = true,
                currentFileName = by.w6.my1drive.utils.UiText.StringResource(by.w6.my1drive.R.string.sync_helper_searching).asString(application),
                progressFraction = 0f,
                totalFiles = 0,
                currentFileIndex = 0
            )
            _syncState.value = null
            operationMutex.withLock {
                try {
                    val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, uri, createIfNotExist = false)
                    if (dir == null || !dir.exists()) throw Exception(application.getString(by.w6.my1drive.R.string.sync_helper_access_failed))

                    var activeUuid = prefs.getString("active_archive_uuid", "") ?: ""
                    if (activeUuid.isEmpty()) {
                        activeUuid = by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(uri) ?: uri.toString().hashCode().toString()
                    }

                    val files = fastListFiles(application, dir.uri) { isCancellationRequested }
                    if (files.isEmpty()) {
                        _syncState.value = by.w6.my1drive.utils.UiText.StringResource(by.w6.my1drive.R.string.sync_helper_finished_no_files)
                        return@withLock
                    }

                    var synced = 0; var skipped = 0
                    val logSb = StringBuilder()
                    logSb.appendLine("Total files found: ${files.size}")

                    // Source of truth: JSON metadata on the OTG drive
                    val jsonEntries = withContext(Dispatchers.IO) {
                        metadataStore.readMetadata(uri)
                    }.toMutableList()

                    val physicalFilesMap = files.associateBy { (it.name.lowercase()) to it.length }
                    var jsonChanged = false
                    val validJsonEntries = mutableListOf<JsonEntry>()

                    // Защита от недозагрузки/ошибки монтирования:
                    // Если флешка вернула 0 файлов, но в JSON метаданных есть записи, не удаляем их
                    if (files.isEmpty() && jsonEntries.isNotEmpty()) {
                        logSb.appendLine("Warning: Directory listing returned empty but JSON has ${jsonEntries.size} entries. Skipping metadata purge to prevent file disappearance.")
                        validJsonEntries.addAll(jsonEntries)
                    } else {
                        for (entry in jsonEntries) {
                            val key = (entry.displayName.lowercase()) to entry.size
                            val physicalFile = physicalFilesMap[key]
                            if (physicalFile != null) {
                                // Имя файла на диске может отличаться регистром, обновим его
                                val actualName = physicalFile.name
                                validJsonEntries.add(entry.copy(displayName = actualName))
                            } else {
                                jsonChanged = true
                                logSb.appendLine("File physically missing or size mismatch on OTG: ${entry.displayName} (expected size: ${entry.size})")
                            }
                        }
                    }

                    val knownHashes = validJsonEntries.map { it.hash }.toHashSet()
                    val knownNamesAndSizesMap = validJsonEntries.associateBy { (it.displayName.lowercase()) to it.size }
                    val newEntries = mutableListOf<JsonEntry>()

                    val existingRoomEntities = db.mediaDao().getByArchiveUuidInChunksSync(activeUuid)
                    val existingRoomMap = existingRoomEntities.associateBy { it.id }.toMutableMap()
                    val batchToInsertManual = mutableListOf<MediaEntity>()

                    withContext(Dispatchers.IO) {
                        for ((idx, file) in files.withIndex()) {
                            if (isCancellationRequested || !isActive) {
                                logSb.appendLine("Sync cancelled by user.")
                                break
                            }
                            val name = file.name
                            val length = file.length

                            _syncProgressState.value = SyncProgressState(
                                isSyncing = true,
                                currentFileName = name,
                                progressFraction = idx.toFloat() / files.size,
                                totalFiles = files.size,
                                currentFileIndex = idx + 1
                            )

                            // Если файл с таким именем и размером уже есть в JSON, то его хэш и метаданные уже известны.
                            // Проверяем наличие в локальной БД Room, при необходимости восстанавливаем запись.
                            val key = (name.lowercase()) to length
                            val entry = knownNamesAndSizesMap[key]
                            if (entry != null) {
                                val otgFileUri = file.uri.toString()
                                val existing = existingRoomMap[entry.hash]
                                if (existing == null) {
                                    val newEntity = MediaEntity(
                                        id = entry.hash,
                                        displayName = entry.displayName,
                                        mimeType = entry.mimeType,
                                        size = entry.size,
                                        dateModified = entry.dateModified,
                                        otgUri = otgFileUri,
                                        thumbnailPath = null,
                                        duration = entry.duration,
                                        originalRelativePath = entry.originalRelativePath,
                                        archiveUuid = activeUuid,
                                        width = entry.width,
                                        height = entry.height
                                    )
                                    batchToInsertManual.add(newEntity)
                                    existingRoomMap[entry.hash] = newEntity
                                } else {
                                    val needUpdateUri = existing.otgUri != otgFileUri || existing.archiveUuid != activeUuid
                                    val needUpdateDims = (existing.width == 0 || existing.height == 0) && (entry.width > 0 && entry.height > 0)
                                    if (needUpdateUri || needUpdateDims) {
                                        val updatedEntity = existing.copy(
                                            otgUri = otgFileUri,
                                            archiveUuid = activeUuid,
                                            width = if (existing.width > 0) existing.width else entry.width,
                                            height = if (existing.height > 0) existing.height else entry.height
                                        )
                                        batchToInsertManual.add(updatedEntity)
                                        existingRoomMap[entry.hash] = updatedEntity
                                    }
                                }
                                if (batchToInsertManual.size >= 500) {
                                    db.mediaDao().insertAll(batchToInsertManual)
                                    batchToInsertManual.clear()
                                }
                                continue
                            }

                            val hash = "${name}_$length"
                            if (hash !in knownHashes) {
                                val mime = file.mimeType.ifEmpty { "image/jpeg" }
                                val defaultPath = if (mime.startsWith("video/")) "Movies/" else "Pictures/"
                                val dims = by.w6.my1drive.utils.ExifHelper.getImageDimensions(application, file.uri, mime)
                                val entry = JsonEntry(
                                    hash = hash,
                                    displayName = name,
                                    mimeType = mime,
                                    size = length,
                                    dateModified = file.lastModified / 1000,
                                    originalRelativePath = defaultPath,
                                    duration = null,
                                    dateArchived = System.currentTimeMillis() / 1000,
                                    width = dims.first,
                                    height = dims.second
                                )
                                newEntries.add(entry)
                                validJsonEntries.add(entry)
                                knownHashes.add(hash)
                                synced++
                            } else {
                                skipped++
                            }
                        }
                        if (batchToInsertManual.isNotEmpty()) {
                            db.mediaDao().insertAll(batchToInsertManual)
                            batchToInsertManual.clear()
                        }
                    }

                    if (newEntries.isNotEmpty()) {
                        jsonChanged = true
                    }

                    // Записываем обновленный JSON если были изменения
                    if (jsonChanged) {
                        withContext(Dispatchers.IO) {
                            metadataStore.writeMetadata(uri, validJsonEntries)
                        }
                    }

                    // Синхронизируем Room для новых и удаленных файлов
                    val finalHashes = validJsonEntries.map { it.hash }.toSet()
                    withContext(Dispatchers.IO) {
                        val batchToInsert = mutableListOf<MediaEntity>()
                        // 1. Добавляем в Room новые
                        for (entry in newEntries) {
                            val physicalFile = physicalFilesMap[(entry.displayName.lowercase()) to entry.size]
                            val otgFileUri = physicalFile?.uri?.toString() ?: ""
                            val existing = existingRoomMap[entry.hash]
                            if (existing == null) {
                                batchToInsert.add(MediaEntity(
                                    id = entry.hash,
                                    displayName = entry.displayName,
                                    mimeType = entry.mimeType,
                                    size = entry.size,
                                    dateModified = entry.dateModified,
                                    otgUri = otgFileUri,
                                    thumbnailPath = null,
                                    duration = entry.duration,
                                    originalRelativePath = entry.originalRelativePath,
                                    archiveUuid = activeUuid
                                ))
                            } else if ((existing.otgUri != otgFileUri && otgFileUri.isNotEmpty()) || existing.archiveUuid != activeUuid) {
                                // Обновляем старый URI в локальной базе
                                batchToInsert.add(existing.copy(
                                    otgUri = otgFileUri,
                                    archiveUuid = activeUuid
                                ))
                            }
                            if (batchToInsert.size >= 500) {
                                db.mediaDao().insertAll(batchToInsert)
                                batchToInsert.clear()
                            }
                        }
                        if (batchToInsert.isNotEmpty()) {
                            db.mediaDao().insertAll(batchToInsert)
                        }
                        // 2. Удаляем из Room пропавшие
                        val deadEntities = mutableListOf<by.w6.my1drive.data.local.MediaEntity>()
                        for (entity in existingRoomEntities) {
                            if (entity.id !in finalHashes) {
                                entity.thumbnailPath?.let { path ->
                                    val file = java.io.File(path)
                                    if (file.exists()) file.delete()
                                }
                                deadEntities.add(entity)
                            }
                        }
                        if (deadEntities.isNotEmpty()) {
                            db.mediaDao().deleteEntities(deadEntities)
                        }
                    }

                    repository.refresh()
                    
                    // Очистка мертвых превью
                    previewCache.cleanupOrphanedPreviews(null)

                    _syncState.value = by.w6.my1drive.utils.UiText.StringResource(by.w6.my1drive.R.string.sync_helper_finished_stats, synced.toString(), (files.size - synced).toString())
                    DebugLogBuffer.log("ManualSync", "Sync complete: imported $synced, total ${files.size}")
                } catch (e: Exception) {
                    val errorMsg = by.w6.my1drive.utils.UiText.StringResource(by.w6.my1drive.R.string.sync_helper_error, e.localizedMessage ?: "")
                    _syncState.value = errorMsg
                    DebugLogBuffer.log("ManualSync", "Exception in manual sync: ${e.localizedMessage}")
                    val sw = java.io.StringWriter()
                    e.printStackTrace(java.io.PrintWriter(sw))
                    DebugLogBuffer.log("ManualSync", "Stacktrace: $sw")
                } finally {
                    _syncProgressState.value = SyncProgressState(isSyncing = false)
                    operationCompleteEvent.tryEmit(Unit)
                }
            }
        }
    }

    fun dismissSync() { _syncState.value = null }

    // ─── Archive queue ───

    data class ArchiveTask(val items: List<MediaItem>, val targetUri: Uri, val isCopy: Boolean = false)
    private val archiveQueue = mutableListOf<ArchiveTask>()
    private var isArchiveJobRunning = false
    private var isCancellationRequested = false

    private var activeArchiveJob: kotlinx.coroutines.Job? = null

    fun cancelArchiving() {
        isCancellationRequested = true
        archiveQueue.clear()
        activeArchiveJob?.cancel()
    }

    /** Add items to archive queue. If nothing is running, starts immediately. */
    fun startArchiving(items: List<MediaItem>, targetUri: Uri, isCopy: Boolean = false) {
        DebugLogBuffer.log("ArchiveSyncHelper", "startArchiving: items=${items.size}, targetUri=$targetUri, isCopy=$isCopy, isArchiveJobRunning=$isArchiveJobRunning")
        if (items.isEmpty()) return
        isCancellationRequested = false
        _archivingItemIds.value = _archivingItemIds.value + items.map { it.id }
        archiveQueue.add(ArchiveTask(items, targetUri, isCopy))
        _archiveState.value = _archiveState.value.copy(pendingQueueSize = archiveQueue.size)
        if (!isArchiveJobRunning) {
            isArchiveJobRunning = true
            activeArchiveJob = scope.launch { processArchiveQueue() }
        }
    }

    private suspend fun processArchiveQueue() {
        try {
            while (archiveQueue.isNotEmpty() && !isCancellationRequested) {
                val task = archiveQueue.removeAt(0)
                _archiveState.value = _archiveState.value.copy(pendingQueueSize = archiveQueue.size)
                performArchiving(task.items, task.targetUri, task.isCopy)
            }
        } finally {
            isArchiveJobRunning = false
            _copiedItemIds.value = emptySet()
            _archiveState.value = ArchiveState(isArchiving = false)
            isCancellationRequested = false
        }
    }

    private suspend fun performArchiving(items: List<MediaItem>, targetUri: Uri, isCopy: Boolean = false) {
        if (items.isEmpty()) return
        operationMutex.withLock {
            val logTag = "ArchiveManager"
            DebugLogBuffer.log(logTag, "Start performArchiving for ${items.size} items. Target: $targetUri, isCopy: $isCopy")
            val targetArchiveName = if (vpsManager.isVpsEnabled()) {
                "VPS"
            } else {
                val activeUuid = prefs.getString("active_archive_uuid", null)
                val archive = if (activeUuid != null) withContext(Dispatchers.IO) { db.archiveDao().getById(activeUuid) } else null
                archive?.name?.takeIf { it.isNotBlank() }
                    ?: archive?.folderName?.takeIf { it.isNotBlank() }
                    ?: by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, targetUri, createIfNotExist = false)?.name
                    ?: androidx.documentfile.provider.DocumentFile.fromTreeUri(application, targetUri)?.name
                    ?: activeUuid?.take(6)?.let { "ID: $it" }
                    ?: ""
            }
            _archiveState.value = ArchiveState(
                isArchiving = true,
                isCopy = isCopy,
                targetArchiveName = targetArchiveName,
                totalFiles = items.size,
                pendingQueueSize = archiveQueue.size
            )
            val copied = mutableListOf<ArchivedInfo>()
            val skipped = mutableListOf<Pair<MediaItem, String>>()
            val errors = mutableListOf<Pair<MediaItem, String>>()

            _copiedItemIds.value = emptySet()
            try {
                for ((index, item) in items.withIndex()) {
                    if (isCancellationRequested) {
                        DebugLogBuffer.log(logTag, "Archiving cancelled by user request. Stopping.")
                        break
                    }
                    DebugLogBuffer.log(logTag, "Processing queue item [${index + 1}/${items.size}]: ${item.displayName}")
                    _archiveState.value = _archiveState.value.copy(
                        currentFileName = item.displayName, currentFileIndex = index + 1, currentStep = ""
                    )
                    var success: ArchivedInfo? = null
                    var itemErr: String? = null
                    var isSkipped = false; var skipReason = ""

                    val archiveFlow = if (vpsManager.isVpsEnabled()) {
                        uploadAndVerifyItemToVps(item)
                    } else {
                        archiveUtil.copyAndVerifyItem(item, targetUri)
                    }

                    archiveFlow.collect { result ->
                        when (result) {
                            is CopyVerifyResult.Progress -> _archiveState.value = _archiveState.value.copy(
                                currentStep = result.step,
                                progressFraction = (index.toFloat() + result.progressFraction) / items.size
                            )
                            is CopyVerifyResult.Success -> success = ArchivedInfo(result.item, result.hash, result.otgUri, result.thumbnailPath)
                            is CopyVerifyResult.Skipped -> { isSkipped = true; skipReason = result.message }
                            is CopyVerifyResult.Error -> itemErr = result.message
                        }
                    }
                    when {
                        success != null -> {
                            copied.add(success)
                            _copiedItemIds.value = _copiedItemIds.value + item.id
                            DebugLogBuffer.log(logTag, "Item success: ${item.displayName}")
                            itemArchivedEvent.tryEmit(item)
                        }
                        isSkipped -> {
                            skipped.add(item to skipReason)
                            DebugLogBuffer.log(logTag, "Item skipped: ${item.displayName}. Reason: $skipReason")
                        }
                        itemErr != null -> {
                            errors.add(item to itemErr)
                            DebugLogBuffer.log(logTag, "Item failed: ${item.displayName}. Error: $itemErr")
                        }
                    }
                    _archivingItemIds.value = _archivingItemIds.value - item.id
                }
            } finally {
                _archivingItemIds.value = _archivingItemIds.value - items.map { it.id }.toSet()
            }

            val skippedFiles = skipped.map { (item, reason) -> item.displayName to reason }
            val errorSummary = if (errors.isNotEmpty()) {
                by.w6.my1drive.utils.UiText.StringResource(by.w6.my1drive.R.string.sync_helper_archiving_failed, errors.size.toString()).asString(application) + 
                errors.joinToString("\n") { "- ${it.first.displayName}: ${it.second.substringBefore("\n")}" }
            } else null

            DebugLogBuffer.log(logTag, "Archiving queue round finished. Copied: ${copied.size}, Skipped: ${skipped.size}, Failed: ${errors.size}")

            if (copied.isNotEmpty()) {
                processArchivedResults(copied, targetUri, errorSummary, isCopy)
                // Уведомить ViewModel об успешно заархивированных файлах
                archiveSuccessEvent.tryEmit(copied.map { it.item })
            } else {
                val combinedError = if (errorSummary != null) by.w6.my1drive.utils.UiText.DynamicString(errorSummary) else by.w6.my1drive.utils.UiText.StringResource(by.w6.my1drive.R.string.sync_helper_archiving_error)
                _archiveState.value = ArchiveState(
                    isArchiving = false,
                    isCopy = isCopy,
                    error = combinedError,
                    skippedFiles = skippedFiles,
                    pendingQueueSize = archiveQueue.size
                )
                operationCompleteEvent.tryEmit(Unit)
            }
        }
    }

    /**
     * Process successfully archived files:
     * 1. Add entry to JSON metadata on the OTG drive (source of truth)
     * 2. Insert into Room (local cache)
     */
    private suspend fun processArchivedResults(list: List<ArchivedInfo>, otgUri: Uri, errorMsg: String? = null, isCopy: Boolean = false) {
        val logTag = "ArchiveManager"
        try {
            DebugLogBuffer.log(logTag, "Processing archived results in database: writing metadata for ${list.size} items")
            val currentTimeSec = System.currentTimeMillis() / 1000
            val jsonEntries = list.map { info ->
                val w = if (info.item.aspectRatio > 0f) (info.item.aspectRatio * 1000).toInt() else 0
                val h = if (info.item.aspectRatio > 0f) 1000 else 0
                JsonEntry(
                    hash = info.hash,
                    displayName = info.item.displayName,
                    mimeType = info.item.mimeType,
                    size = info.item.size,
                    dateModified = info.item.dateModified,
                    originalRelativePath = info.item.originalRelativePath,
                    duration = info.item.duration,
                    dateArchived = currentTimeSec,
                    width = w,
                    height = h
                )
            }
            if (!vpsManager.isVpsEnabled()) {
                metadataStore.addEntries(otgUri, jsonEntries)
                DebugLogBuffer.log(logTag, "Added entries to JSON metadata on OTG drive")
            }

            // 2. Insert into Room (local cache) in a single batch transaction
            val activeUuid = prefs.getString("active_archive_uuid", "") ?: ""
            val entitiesToInsert = list.map { info ->
                val item = info.item
                by.w6.my1drive.data.local.MediaEntity(
                    id = info.hash,
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    size = item.size,
                    dateModified = item.dateModified,
                    otgUri = info.otgUri,
                    thumbnailPath = info.thumbnailPath,
                    duration = item.duration,
                    originalRelativePath = info.item.originalRelativePath ?: item.originalRelativePath,
                    dateArchived = currentTimeSec,
                    archiveUuid = activeUuid,
                    width = if (item.aspectRatio > 0f) (item.aspectRatio * 1000).toInt() else 0,
                    height = if (item.aspectRatio > 0f) 1000 else 0
                )
            }
            db.mediaDao().insertAll(entitiesToInsert)
            repository.refresh()
            DebugLogBuffer.log(logTag, "Batch inserted ${entitiesToInsert.size} items to Room database")

            _archiveState.value = ArchiveState(
                isArchiving = false,
                isCopy = isCopy,
                error = errorMsg?.let { by.w6.my1drive.utils.UiText.DynamicString(it) },
                pendingQueueSize = archiveQueue.size
            )
        } catch (e: Exception) {
            DebugLogBuffer.log(logTag, "Error processing archived results: ${e.localizedMessage}")
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            DebugLogBuffer.log(logTag, "Stacktrace: $sw")
            
            _archiveState.value = _archiveState.value.copy(
                isArchiving = false, error = e.localizedMessage?.let { by.w6.my1drive.utils.UiText.DynamicString(it) },
                pendingQueueSize = archiveQueue.size
            )
        } finally {
            operationCompleteEvent.tryEmit(Unit)
        }
    }

    fun dismissError() { _archiveState.value = _archiveState.value.copy(error = null) }

    private fun uploadAndVerifyItemToVps(item: MediaItem): kotlinx.coroutines.flow.Flow<CopyVerifyResult> = kotlinx.coroutines.flow.flow {
        val logTag = "VpsArchiveCopy"
        try {
            DebugLogBuffer.log(logTag, "Start uploadAndVerifyItemToVps: ${item.displayName}, size=${item.size}, mime=${item.mimeType}")
            emit(CopyVerifyResult.Progress(item.displayName, "preparing", 0.0f))

            if (item.size <= 0) {
                emit(CopyVerifyResult.Skipped(item, "SKIP: source has zero size"))
                return@flow
            }

            val input = try {
                application.contentResolver.openInputStream(item.uri)
                    ?: throw Exception("Failed to open input stream for ${item.displayName}")
            } catch (e: Exception) {
                emit(CopyVerifyResult.Skipped(item, "SKIP: source file not found on device: ${e.localizedMessage}"))
                return@flow
            }

            emit(CopyVerifyResult.Progress(item.displayName, "uploading", 0.1f))

            val uploadResult = vpsManager.uploadFile(input, item.displayName) { progress ->
                // Emit progress
                val fraction = 0.1f + (progress.toFloat() / item.size) * 0.8f
                // We could emit progress fractions up to 0.9f here
            }

            if (uploadResult.isFailure) {
                throw uploadResult.exceptionOrNull() ?: Exception("Upload failed")
            }

            val remotePath = uploadResult.getOrNull() ?: ""

            emit(CopyVerifyResult.Progress(item.displayName, "verifying", 0.9f))

            val srcHash = "${item.size}_${item.dateModified}"

            // Pre-cache thumbnail
            val precachedPath = try {
                archiveUtil.precacheThumbnail(item, srcHash)
            } catch (ex: Exception) {
                null
            }

            emit(CopyVerifyResult.Success(item, srcHash, remotePath, precachedPath))
        } catch (e: Exception) {
            DebugLogBuffer.log(logTag, "Error uploading to VPS: ${e.message}")
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            emit(CopyVerifyResult.Error(item.displayName, "${e.javaClass.name}: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun syncAllThumbnails(
        activeUuid: String,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean = { false },
        throttleMs: Long = 350L,
        batchSize: Int = 25,
        onProgress: (current: Int, total: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Общее кол-во для прогресса — запрашиваем COUNT, не весь список
        val total = db.mediaDao().getWithoutPreviewCount(activeUuid)
        if (total == 0) return@withContext

        val pDir = previewCache.previewDir
        val pendingBatch = mutableListOf<MediaEntity>()
        val pendingEvents = mutableListOf<Pair<String, String>>()
        var lastFlushTime = System.currentTimeMillis()

        fun flushBatch() {
            if (pendingBatch.isNotEmpty()) {
                db.mediaDao().insertAll(pendingBatch)
                pendingEvents.forEach { previewCachedEvent.tryEmit(it) }
                pendingBatch.clear()
                pendingEvents.clear()
                lastFlushTime = System.currentTimeMillis()
            }
        }

        fun resolveDimensions(existing: MediaEntity, file: File): Pair<Int, Int> {
            if (existing.width > 0 && existing.height > 0) return Pair(existing.width, existing.height)
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            return if (opts.outWidth > 0 && opts.outHeight > 0) Pair(opts.outWidth, opts.outHeight) else Pair(0, 0)
        }

        // Постраничная обработка вместо загрузки 50k записей в одну List — fix для OOM
        val pageSize = 100
        var offset = 0
        var idx = 0
        try {
            outer@ while (true) {
                val page = db.mediaDao().getWithoutPreviewPage(activeUuid, limit = pageSize, offset = offset)
                if (page.isEmpty()) break
                for (entity in page) {
                    if (isCancelled() || isFreeSpaceLow() || isBatteryLow()) {
                        DebugLogBuffer.log("ArchiveSyncHelper", "Thumbnail sync cancelled (low space/battery/cancelled)")
                        break@outer
                    }

                    // Heap safeguard: if memory usage is critical, give GC time or break loop to avoid OOM
                    if (isHeapLow()) {
                        DebugLogBuffer.log("ArchiveSyncHelper", "Heap usage high (>75%), pausing thumbnail sync and requesting GC")
                        System.gc()
                        delay(1000)
                        if (isHeapLow()) {
                            DebugLogBuffer.log("ArchiveSyncHelper", "Heap usage remains critical (>75%), halting thumbnail sync to prevent OOM")
                            break@outer
                        }
                    }

                    // Respect pause (e.g. while user is actively scrolling the gallery)
                    while (isPaused()) {
                        if (isCancelled()) break
                        delay(250)
                    }
                    if (isCancelled()) break@outer

                    val uriStr = entity.otgUri ?: ""
                    if (uriStr.isEmpty()) continue

                    val cacheFile = previewCache.cacheFileFor(entity.id)

                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        // Already cached locally, ensure it is also saved to OTG previews if missing
                        try {
                            val uri = Uri.parse(uriStr)
                            by.w6.my1drive.utils.OtgFolderResolver.trySavePreviewToOtg(application, uri, entity.id, cacheFile)
                        } catch (_: Exception) {}

                        val (w, h) = resolveDimensions(entity, cacheFile)
                        val updated = entity.copy(
                            thumbnailPath = cacheFile.absolutePath,
                            lastAccessed = System.currentTimeMillis(),
                            width = w,
                            height = h
                        )
                        pendingBatch.add(updated)
                        pendingEvents.add(Pair(entity.id, cacheFile.absolutePath))
                    } else {
                        val uri = Uri.parse(uriStr)
                        var loaded = false

                        // 1. Fast-path: Check if small preview already exists in .previews on OTG
                        try {
                            pDir.mkdirs()
                            if (by.w6.my1drive.utils.OtgFolderResolver.tryCopyPreviewFromOtg(application, uri, entity.id, cacheFile)) {
                                val (w, h) = resolveDimensions(entity, cacheFile)
                                val updated = entity.copy(
                                    thumbnailPath = cacheFile.absolutePath,
                                    lastAccessed = System.currentTimeMillis(),
                                    width = w,
                                    height = h
                                )
                                pendingBatch.add(updated)
                                pendingEvents.add(Pair(entity.id, cacheFile.absolutePath))
                                loaded = true
                            }
                        } catch (e: Exception) {
                            DebugLogBuffer.log("ArchiveSyncHelper", "Failed fast copy from OTG: ${e.message}")
                        }

                        // 2. Slow-path: Decode original file, scale, and save to BOTH local cache and OTG .previews
                        if (!loaded) {
                            try {
                                val bitmap = generateThumbnailHelper(uri, entity.mimeType)
                                if (bitmap != null) {
                                    pDir.mkdirs()
                                    cacheFile.outputStream().buffered().use { out ->
                                        val scaled = scaleBitmapHelper(bitmap, 256)
                                        scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 65, out)
                                        if (scaled !== bitmap) scaled.recycle()
                                    }
                                    bitmap.recycle()

                                    // Save to OTG drive .previews as well
                                    by.w6.my1drive.utils.OtgFolderResolver.trySavePreviewToOtg(application, uri, entity.id, cacheFile)

                                    val (w, h) = resolveDimensions(entity, cacheFile)
                                    val updated = entity.copy(
                                        thumbnailPath = cacheFile.absolutePath,
                                        lastAccessed = System.currentTimeMillis(),
                                        width = w,
                                        height = h
                                    )
                                    pendingBatch.add(updated)
                                    pendingEvents.add(Pair(entity.id, cacheFile.absolutePath))
                                }
                            } catch (e: Exception) {
                                DebugLogBuffer.log("ArchiveSyncHelper", "Failed thumbnail sync for ${entity.id}: ${e.message}")
                            }
                        }
                    }

                    val now = System.currentTimeMillis()
                    if (pendingBatch.size >= batchSize || (now - lastFlushTime >= 15_000L && pendingBatch.isNotEmpty())) {
                        flushBatch()
                    }

                    // throttle to keep CPU and bus completely cool
                    delay(throttleMs)

                    idx++
                    withContext(Dispatchers.Main) {
                        onProgress(idx, total)
                    }
                }
                // Если страница не полная — все записи без превью обработаны
                flushBatch()
                if (page.size < pageSize) break
                offset += pageSize
            }
        } finally {
            flushBatch()
        }
    }

    private fun isFreeSpaceLow(): Boolean {
        return try {
            val stat = android.os.StatFs(application.filesDir.absolutePath)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            bytesAvailable < 500L * 1024 * 1024
        } catch (_: Exception) {
            false
        }
    }

    private fun isBatteryLow(): Boolean {
        return try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = application.registerReceiver(null, filter) ?: return false
            val level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level / scale.toFloat()
            val status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            batteryPct < 0.2f && !isCharging
        } catch (_: Exception) {
            false
        }
    }

    private fun isHeapLow(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val usedBytes = runtime.totalMemory() - runtime.freeMemory()
            val maxBytes = runtime.maxMemory()
            (usedBytes.toDouble() / maxBytes.toDouble()) > 0.75
        } catch (_: Exception) {
            false
        }
    }

    private fun generateThumbnailHelper(uri: Uri, mimeType: String): Bitmap? {
        return if (mimeType.startsWith("video")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(application, uri)
                // getScaledFrameAtTime (API 27+) декодирует кадр сразу в нужный размер,
                // не загружая полный 4K/8K bitmap в heap — fix для OOM
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512)
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        } else {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                application.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, boundsOpts)
                }
                val sampleSize = calculateSampleSizeHelper(boundsOpts.outWidth, boundsOpts.outHeight, 256)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = application.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOpts)
                }
                if (decoded != null) {
                    by.w6.my1drive.utils.ExifHelper.rotateBitmapIfNeeded(application, uri, decoded)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun scaleBitmapHelper(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun calculateSampleSizeHelper(width: Int, height: Int, reqSize: Int): Int {
        var size = 1
        if (width > reqSize || height > reqSize) {
            val halfW = width / 2
            val halfH = height / 2
            while (halfW / size >= reqSize && halfH / size >= reqSize) size *= 2
        }
        return size
    }
}
