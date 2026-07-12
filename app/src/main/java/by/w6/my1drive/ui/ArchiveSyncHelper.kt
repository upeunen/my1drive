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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import by.w6.my1drive.utils.VpsConnectionManager


/** Helper for archive/manual-sync operations extracted from GalleryViewModel */
class ArchiveSyncHelper(
    private val application: Application,
    private val db: AppDatabase,
    private val repository: MediaRepository,
    private val archiveUtil: OtgArchiveUtil,
    private val prefs: android.content.SharedPreferences,
    private val previewCache: PreviewCacheManager,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onOperationComplete: () -> Unit = {},
    private val onArchiveSuccess: (List<MediaItem>) -> Unit = {},
    private val onItemArchived: ((MediaItem) -> Unit)? = null,
    private val onPreviewCached: ((hash: String, path: String) -> Unit)? = null
) {

    companion object {
        private val operationMutex = Mutex()
        
        data class FastDocumentFile(
            val uri: android.net.Uri,
            val name: String,
            val length: Long,
            val mimeType: String,
            val lastModified: Long
        )
        
        fun fastListFiles(context: android.content.Context, dirUri: android.net.Uri, isCancelled: () -> Boolean = { false }): List<FastDocumentFile> {
            val results = mutableListOf<FastDocumentFile>()
            try {
                val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                    dirUri,
                    android.provider.DocumentsContract.getDocumentId(dirUri)
                )
                val projection = arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_SIZE,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                    val mimeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val modIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        if (isCancelled()) break
                        val docId = cursor.getString(idIdx)
                        val mime = cursor.getString(mimeIdx) ?: ""
                        if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) continue
                        val name = cursor.getString(nameIdx) ?: continue
                        
                        if (name == ".my1drive_uuid" || name == ".my1drive_uuid.txt" || 
                            name == ".my1drive_db.json" || name == "my1drive_db.json") continue
                        
                        val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(dirUri, docId)
                        val size = cursor.getLong(sizeIdx)
                        val modified = cursor.getLong(modIdx)
                        
                        results.add(FastDocumentFile(docUri, name, size, mime, modified))
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

    private val _syncState = MutableStateFlow<String?>(null)
    val syncState: StateFlow<String?> = _syncState.asStateFlow()

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
     * Отменяет текущую синхронизацию (например, при извлечении диска).
     */
    fun cancelOperations() {
        activeSyncJob?.cancel()
        activeSyncJob = null

        isSilentSyncing = false
        _syncProgressState.value = SyncProgressState(isSyncing = false)
        isCancellationRequested = true
        by.w6.my1drive.utils.DebugLogBuffer.log("ArchiveSyncHelper", "cancelOperations: sync, archive and preview jobs cancelled")
    }

    /**
     * Silent auto-sync:
     * 1. Копирует все записи из JSON (источник истины на OTG) в Room на устройстве.
     * 2. Сканирует файлы на флешке, добавляет в JSON и Room те, что ещё не учтены.
     *
     * Это гарантирует, что после createNewArchive / переустановки приложения
     * все ранее заархивированные файлы появятся в интерфейсе.
     */
    fun silentSyncArchive(otgDirectoryUri: Uri?) {
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
                        DebugLogBuffer.log(logTag, "Start silentSyncArchive: targetUri=$uri")


                        val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, uri, createIfNotExist = false)

                        val uuidFromPrefs = prefs.getString("active_archive_uuid", "") ?: ""
                        val activeUuid: String = if (uuidFromPrefs.isNotEmpty()) {
                            uuidFromPrefs
                        } else {
                            by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(uri) ?: uri.toString().hashCode().toString()
                        }
                        DebugLogBuffer.log(logTag, "activeUuid resolved: $activeUuid")

                        // ── Шаг 1: Чтение JSON метаданных ──
                        val jsonEntries = metadataStore.readMetadata(uri)
                        DebugLogBuffer.log(logTag, "Read metadata: ${jsonEntries.size} JSON entries")

                        val metadataExists = metadataStore.metadataExists(uri)
                        val physicalFiles = if (dir != null && dir.exists()) {
                            fastListFiles(application, dir.uri) { isCancellationRequested }
                        } else emptyList()

                        DebugLogBuffer.log(logTag, "Metadata exists: $metadataExists. Physical files found: ${physicalFiles.size}")

                        if (jsonEntries.isEmpty() && !metadataExists && physicalFiles.isEmpty()) {
                            DebugLogBuffer.log(logTag, "No metadata file and no physical files found on OTG. Skipping Room database sync to preserve cache.")
                            return@withLock
                        }

                        // ── Шаг 1.5: Сканирование физической папки на флешке ──
                        val validJsonEntries = jsonEntries.toMutableList()
                        var jsonChanged = false

                        if (dir != null && dir.exists()) {
                            val files = physicalFiles

                            val knownNamesMap = jsonEntries.associateBy { it.displayName.lowercase() }
                            val knownHashes = jsonEntries.map { it.hash }.toHashSet()

                            for (file in files) {
                                if (isCancellationRequested || !isActive) {
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
                                    val mime = file.mimeType
                                    val defaultPath = if (mime.startsWith("video/")) "Movies/" else "Pictures/"
                                    val newEntry = JsonEntry(
                                        hash = hash,
                                        displayName = name,
                                        mimeType = mime,
                                        size = file.length,
                                        dateModified = file.lastModified / 1000,
                                        originalRelativePath = defaultPath,
                                        duration = null,
                                        dateArchived = System.currentTimeMillis() / 1000
                                    )
                                    validJsonEntries.add(newEntry)
                                    knownHashes.add(hash)
                                    jsonChanged = true
                                    DebugLogBuffer.log(logTag, "Scanned and added new file to metadata: $name (hash=$hash)")
                                }
                            }


                            if (jsonChanged) {
                                metadataStore.writeMetadata(uri, validJsonEntries)
                                DebugLogBuffer.log(logTag, "Saved updated metadata JSON with new scanned files")
                            }
                        }

                        // ── Шаг 2: Синхронизация Room с JSON данными ──
                        val finalHashes = validJsonEntries.map { it.hash }.toSet()
                        var roomModified = false
                        var insertedToRoom = 0

                        val physicalUrisMap = physicalFiles.associate { 
                            (it.name.lowercase() to it.length) to it.uri.toString() 
                        }

                        val batchToInsert = mutableListOf<MediaEntity>()

                        for (entry in validJsonEntries) {
                            if (isCancellationRequested || !isActive) {
                                DebugLogBuffer.log(logTag, "Silent sync cancelled during Room update")
                                break
                            }
                            val existing = db.mediaDao().getById(entry.hash)
                            if (existing == null) {
                                val key = (entry.displayName.lowercase()) to entry.size
                                val otgFileUri = physicalUrisMap[key] ?: ""

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
                                    dateArchived = entry.dateArchived,
                                    archiveUuid = activeUuid
                                ))
                                insertedToRoom++
                                roomModified = true
                            } else {
                                val key = (entry.displayName.lowercase()) to entry.size
                                val resolvedUri = physicalUrisMap[key] ?: existing.otgUri ?: ""

                                if (existing.displayName != entry.displayName || 
                                    existing.size != entry.size || 
                                    existing.dateModified != entry.dateModified ||
                                    existing.otgUri != resolvedUri ||
                                    existing.archiveUuid != activeUuid
                                ) {
                                    batchToInsert.add(existing.copy(
                                        displayName = entry.displayName,
                                        mimeType = entry.mimeType,
                                        size = entry.size,
                                        dateModified = entry.dateModified,
                                        otgUri = resolvedUri,
                                        duration = entry.duration,
                                        originalRelativePath = entry.originalRelativePath,
                                        dateArchived = entry.dateArchived,
                                        archiveUuid = activeUuid
                                    ))
                                    roomModified = true
                                }
                            }
                            
                            if (batchToInsert.size >= 500) {
                                db.mediaDao().insertAll(batchToInsert)
                                batchToInsert.clear()
                            }
                        }
                        
                        if (batchToInsert.isNotEmpty()) {
                            db.mediaDao().insertAll(batchToInsert)
                        }
                        if (insertedToRoom > 0) {
                            DebugLogBuffer.log(logTag, "Added $insertedToRoom missing entries from JSON to Room")
                        }

                        // Удаляем из Room записи, которых больше нет в JSON
                        val allRoomEntities = db.mediaDao().getAllSync().filter { it.archiveUuid == activeUuid }
                        var deletedFromRoom = 0
                        for (entity in allRoomEntities) {
                            if (entity.id !in finalHashes) {
                                entity.thumbnailPath?.let { path ->
                                    val file = java.io.File(path)
                                    if (file.exists()) file.delete()
                                }
                                db.mediaDao().delete(entity)
                                deletedFromRoom++
                                roomModified = true
                            }
                        }
                        if (deletedFromRoom > 0) {
                            DebugLogBuffer.log(logTag, "Removed $deletedFromRoom dead entries from Room database")
                        }

                        if (roomModified) {
                            repository.refresh()
                        }
                        DebugLogBuffer.log(logTag, "Silent sync finished successfully")

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
                onOperationComplete()
            }
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
                currentFileName = "Поиск файлов на OTG...",
                progressFraction = 0f,
                totalFiles = 0,
                currentFileIndex = 0
            )
            _syncState.value = null
            operationMutex.withLock {
                try {
                    val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, uri, createIfNotExist = false)
                    if (dir == null || !dir.exists()) throw Exception("Не удалось получить доступ к OTG накопителю")

                    var activeUuid = prefs.getString("active_archive_uuid", "") ?: ""
                    if (activeUuid.isEmpty()) {
                        activeUuid = by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(uri) ?: uri.toString().hashCode().toString()
                    }

                    val files = fastListFiles(application, dir.uri) { isCancellationRequested }
                    if (files.isEmpty()) {
                        _syncState.value = "Синхронизация завершена: файлов нет."
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
                                val existing = db.mediaDao().getById(entry.hash)
                                if (existing == null) {
                                    db.mediaDao().insert(MediaEntity(
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
                                } else if (existing.otgUri != otgFileUri || existing.archiveUuid != activeUuid) {
                                    // Обновляем кэш, если флешку переподключили и URI изменился
                                    db.mediaDao().insert(existing.copy(
                                        otgUri = otgFileUri,
                                        archiveUuid = activeUuid
                                    ))
                                }
                                continue
                            }

                            val hash = "${name}_$length"
                            if (hash !in knownHashes) {
                                val mime = file.mimeType.ifEmpty { "image/jpeg" }
                                val defaultPath = if (mime.startsWith("video/")) "Movies/" else "Pictures/"
                                val entry = JsonEntry(
                                    hash = hash,
                                    displayName = name,
                                    mimeType = mime,
                                    size = length,
                                    dateModified = file.lastModified / 1000,
                                    originalRelativePath = defaultPath,
                                    duration = null,
                                    dateArchived = System.currentTimeMillis() / 1000
                                )
                                newEntries.add(entry)
                                validJsonEntries.add(entry)
                                knownHashes.add(hash)
                                synced++
                            } else {
                                skipped++
                            }
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
                            val existing = db.mediaDao().getById(entry.hash)
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
                        val allRoomEntities = db.mediaDao().getAllSync().filter { it.archiveUuid == activeUuid }
                        for (entity in allRoomEntities) {
                            if (entity.id !in finalHashes) {
                                entity.thumbnailPath?.let { path ->
                                    val file = java.io.File(path)
                                    if (file.exists()) file.delete()
                                }
                                db.mediaDao().delete(entity)
                            }
                        }
                    }

                    repository.refresh()
                    
                    // Очистка мертвых превью
                    previewCache.cleanupOrphanedPreviews(null)

                    _syncState.value = "Синхронизация завершена.\n\nИмпортировано новых файлов: $synced\nПропущено/проверено: ${files.size - synced}"
                    DebugLogBuffer.log("ManualSync", "Sync complete: imported $synced, total ${files.size}")
                } catch (e: Exception) {
                    val errorMsg = "Ошибка синхронизации: ${e.localizedMessage}"
                    _syncState.value = errorMsg
                    DebugLogBuffer.log("ManualSync", "Exception in manual sync: ${e.localizedMessage}")
                    val sw = java.io.StringWriter()
                    e.printStackTrace(java.io.PrintWriter(sw))
                    DebugLogBuffer.log("ManualSync", "Stacktrace: $sw")
                } finally {
                    _syncProgressState.value = SyncProgressState(isSyncing = false)
                    onOperationComplete()
                }
            }
        }
    }

    fun dismissSync() { _syncState.value = null }

    // ─── Archive queue ───

    private val archiveQueue = mutableListOf<Pair<List<MediaItem>, Uri>>()
    private var isArchiveJobRunning = false
    private var isCancellationRequested = false

    fun cancelArchiving() {
        isCancellationRequested = true
        archiveQueue.clear()
    }

    /** Add items to archive queue. If nothing is running, starts immediately. */
    fun startArchiving(items: List<MediaItem>, targetUri: Uri) {
        DebugLogBuffer.log("ArchiveSyncHelper", "startArchiving: items=${items.size}, targetUri=$targetUri, isArchiveJobRunning=$isArchiveJobRunning")
        if (items.isEmpty()) return
        isCancellationRequested = false
        _archivingItemIds.value = _archivingItemIds.value + items.map { it.id }
        archiveQueue.add(items to targetUri)
        _archiveState.value = _archiveState.value.copy(pendingQueueSize = archiveQueue.size)
        if (!isArchiveJobRunning) {
            isArchiveJobRunning = true
            scope.launch { processArchiveQueue() }
        }
    }

    private suspend fun processArchiveQueue() {
        try {
            while (archiveQueue.isNotEmpty() && !isCancellationRequested) {
                val (items, targetUri) = archiveQueue.removeAt(0)
                _archiveState.value = _archiveState.value.copy(pendingQueueSize = archiveQueue.size)
                performArchiving(items, targetUri)
            }
        } finally {
            isArchiveJobRunning = false
            _copiedItemIds.value = emptySet()
            _archiveState.value = ArchiveState(isArchiving = false)
            isCancellationRequested = false
        }
    }

    private suspend fun performArchiving(items: List<MediaItem>, targetUri: Uri) {
        if (items.isEmpty()) return
        operationMutex.withLock {
            val logTag = "ArchiveManager"
            DebugLogBuffer.log(logTag, "Start performArchiving for ${items.size} items. Target: $targetUri")
            _archiveState.value = ArchiveState(
                isArchiving = true, totalFiles = items.size,
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
                            onItemArchived?.invoke(item)
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
                "Не удалось заархивировать ${errors.size} файл(ов):\n" + 
                errors.joinToString("\n") { "- ${it.first.displayName}: ${it.second.substringBefore("\n")}" }
            } else null

            DebugLogBuffer.log(logTag, "Archiving queue round finished. Copied: ${copied.size}, Skipped: ${skipped.size}, Failed: ${errors.size}")

            if (copied.isNotEmpty()) {
                processArchivedResults(copied, targetUri, errorSummary)
                // Уведомить ViewModel об успешно заархивированных файлах
                onArchiveSuccess(copied.map { it.item })
            } else {
                val combinedError = errorSummary ?: "Ошибка архивирования"
                _archiveState.value = ArchiveState(
                    isArchiving = false, error = combinedError,
                    skippedFiles = skippedFiles,
                    pendingQueueSize = archiveQueue.size
                )
                onOperationComplete()
            }
        }
    }

    /**
     * Process successfully archived files:
     * 1. Add entry to JSON metadata on the OTG drive (source of truth)
     * 2. Insert into Room (local cache)
     */
    private suspend fun processArchivedResults(list: List<ArchivedInfo>, otgUri: Uri, errorMsg: String? = null) {
        val logTag = "ArchiveManager"
        try {
            DebugLogBuffer.log(logTag, "Processing archived results in database: writing metadata for ${list.size} items")
            val currentTimeSec = System.currentTimeMillis() / 1000
            val jsonEntries = list.map { info ->
                JsonEntry(
                    hash = info.hash,
                    displayName = info.item.displayName,
                    mimeType = info.item.mimeType,
                    size = info.item.size,
                    dateModified = info.item.dateModified,
                    originalRelativePath = info.item.originalRelativePath,
                    duration = info.item.duration,
                    dateArchived = currentTimeSec
                )
            }
            if (!vpsManager.isVpsEnabled()) {
                metadataStore.addEntries(otgUri, jsonEntries)
                DebugLogBuffer.log(logTag, "Added entries to JSON metadata on OTG drive")
            }

            // 2. Insert into Room (local cache)
            for (info in list) {
                repository.insertArchivedItem(
                    info.item, info.otgUri, info.hash,
                    info.thumbnailPath, info.item.originalRelativePath,
                    currentTimeSec
                )
                DebugLogBuffer.log(logTag, "Inserted item to local DB: ${info.item.displayName} (hash=${info.hash})")
            }

            _archiveState.value = ArchiveState(
                isArchiving = false, 
                error = errorMsg,
                pendingQueueSize = archiveQueue.size
            )
        } catch (e: Exception) {
            DebugLogBuffer.log(logTag, "Error processing archived results: ${e.localizedMessage}")
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            DebugLogBuffer.log(logTag, "Stacktrace: $sw")
            
            _archiveState.value = _archiveState.value.copy(
                isArchiving = false, error = e.localizedMessage,
                pendingQueueSize = archiveQueue.size
            )
        } finally {
            onOperationComplete()
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

            val digest = MessageDigest.getInstance("SHA-256")
            val input = try {
                application.contentResolver.openInputStream(item.uri)
                    ?: throw Exception("Failed to open input stream for ${item.displayName}")
            } catch (e: Exception) {
                emit(CopyVerifyResult.Skipped(item, "SKIP: source file not found on device: ${e.localizedMessage}"))
                return@flow
            }

            var bytesUploaded = 0L
            val hashingInputStream = object : java.io.FilterInputStream(input) {
                override fun read(): Int {
                    val b = super.read()
                    if (b != -1) {
                        digest.update(b.toByte())
                        bytesUploaded++
                    }
                    return b
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val readBytes = super.read(b, off, len)
                    if (readBytes != -1) {
                        digest.update(b, off, readBytes)
                        bytesUploaded += readBytes
                    }
                    return readBytes
                }
            }

            emit(CopyVerifyResult.Progress(item.displayName, "uploading", 0.1f))

            val uploadResult = vpsManager.uploadFile(hashingInputStream, item.displayName) { progress ->
                // Emit progress
                val fraction = 0.1f + (progress.toFloat() / item.size) * 0.8f
                // We could emit progress fractions up to 0.9f here
            }

            if (uploadResult.isFailure) {
                throw uploadResult.exceptionOrNull() ?: Exception("Upload failed")
            }

            val remotePath = uploadResult.getOrNull() ?: ""

            emit(CopyVerifyResult.Progress(item.displayName, "verifying", 0.9f))

            val srcHash = digest.digest().joinToString("") { "%02x".format(it) }

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
        onProgress: (current: Int, total: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val missingItems = db.mediaDao().getWithoutPreview(activeUuid, limit = 50_000)
        val total = missingItems.size
        if (total == 0) return@withContext

        // Lift cache limit until next cache clearance
        prefs.edit().putBoolean("archive_unlimited_cache_$activeUuid", true).apply()

        val pDir = previewCache.previewDir

        for ((idx, entity) in missingItems.withIndex()) {
            if (isCancelled()) {
                DebugLogBuffer.log("ArchiveSyncHelper", "Thumbnail sync cancelled")
                break
            }
            val uriStr = entity.otgUri ?: ""
            if (uriStr.isEmpty()) continue

            val cacheFile = previewCache.cacheFileFor(entity.id)
            var success = false

            if (cacheFile.exists() && cacheFile.length() > 0) {
                // Already cached
                db.mediaDao().insert(entity.copy(thumbnailPath = cacheFile.absolutePath))
                onPreviewCached?.invoke(entity.id, cacheFile.absolutePath)
                success = true
            } else {
                try {
                    val uri = Uri.parse(uriStr)
                    val bitmap = generateThumbnailHelper(uri, entity.mimeType)
                    if (bitmap != null) {
                        pDir.mkdirs()
                        cacheFile.outputStream().buffered().use { out ->
                            val scaled = scaleBitmapHelper(bitmap, 256)
                            scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 65, out)
                            if (scaled !== bitmap) scaled.recycle()
                        }
                        bitmap.recycle()
                        db.mediaDao().insert(entity.copy(thumbnailPath = cacheFile.absolutePath))
                        onPreviewCached?.invoke(entity.id, cacheFile.absolutePath)
                        success = true
                    }
                } catch (e: Exception) {
                    DebugLogBuffer.log("ArchiveSyncHelper", "Failed thumbnail sync for ${entity.id}: ${e.message}")
                }
            }
            
            // throttle slightly to keep CPU cool
            kotlinx.coroutines.delay(50)
            
            withContext(Dispatchers.Main) {
                onProgress(idx + 1, total)
            }
        }
    }

    private fun generateThumbnailHelper(uri: Uri, mimeType: String): Bitmap? {
        return if (mimeType.startsWith("video")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(application, uri)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
                application.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOpts)
                }
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
