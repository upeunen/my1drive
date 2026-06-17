package by.w6.my1drive.ui

import android.app.Application
import android.net.Uri
import java.io.File
import androidx.documentfile.provider.DocumentFile
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        private val onArchiveSuccess: (List<MediaItem>) -> Unit = {}
    ) {

    companion object {
        private val operationMutex = Mutex()
    }

    private val metadataStore = ArchiveMetadataStore(application)

    private val _syncState = MutableStateFlow<String?>(null)
    val syncState: StateFlow<String?> = _syncState.asStateFlow()

    private val _archiveState = MutableStateFlow(ArchiveState())
        val archiveState: StateFlow<ArchiveState> = _archiveState.asStateFlow()

    private val _missingFilesNotification = MutableStateFlow<List<String>?>(null)
    val missingFilesNotification: StateFlow<List<String>?> = _missingFilesNotification.asStateFlow()

    private val _autoSyncAddedCount = MutableStateFlow(0)
        val autoSyncAddedCount: StateFlow<Int> = _autoSyncAddedCount.asStateFlow()

    var isSilentSyncing = false

    private val PREF_MISSING_FILES_DISMISSED = "missing_files_dismissed"
    private val PREF_MISSING_FILES_HASH = "missing_files_hash"

    // ─── Silent auto-sync ───

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
        isSilentSyncing = true
        val logTag = "SilentSync"
        scope.launch(Dispatchers.IO) {
            try {
                operationMutex.withLock {
                    try {
                        DebugLogBuffer.log(logTag, "Start silentSyncArchive: targetUri=$uri")
                        val dir = DocumentFile.fromTreeUri(application, uri) 
                            ?: throw Exception("Failed to access tree Uri $uri")
                        if (!dir.exists()) {
                            DebugLogBuffer.log(logTag, "Directory does not exist: $uri")
                            return@withLock
                        }

                        // ── Шаг 0: Быстрый сбор файлов с флешки в карту ──
                        val otgFiles = dir.listFiles().filter {
                            !it.isDirectory && it.name != null &&
                            it.name != ".my1drive_uuid" && it.name != ".my1drive_db.json"
                        }
                        DebugLogBuffer.log(logTag, "Found ${otgFiles.size} files on OTG drive")

                        // Создаем карту физических файлов для регистронезависимого сопоставления по имени и размеру
                        // Ключ: (имя в нижнем регистре, размер) -> DocumentFile
                        val physicalFilesMap = otgFiles.associateBy { (it.name?.lowercase() ?: "") to it.length() }

                        // ── Шаг 2: Чтение JSON метаданных ──
                        val jsonEntries = metadataStore.readMetadata(uri)
                        DebugLogBuffer.log(logTag, "Read metadata: ${jsonEntries.size} JSON entries")

                        var jsonChanged = false
                        val validJsonEntries = mutableListOf<JsonEntry>()

                        // Защита от недозагрузки/ошибки монтирования:
                        // Если флешка вернула 0 файлов, но в JSON метаданных есть записи, не удаляем их
                        if (otgFiles.isEmpty() && jsonEntries.isNotEmpty()) {
                            DebugLogBuffer.log(logTag, "Directory listing returned empty but JSON has ${jsonEntries.size} entries. Skipping metadata purge to prevent file disappearance.")
                            validJsonEntries.addAll(jsonEntries)
                        } else {
                            for (entry in jsonEntries) {
                                val key = (entry.displayName.lowercase()) to entry.size
                                val physicalFile = physicalFilesMap[key]
                                if (physicalFile != null) {
                                    // Имя файла на накопителе может иметь другой регистр символов, берем имя с диска
                                    val actualName = physicalFile.name ?: entry.displayName
                                    validJsonEntries.add(entry.copy(displayName = actualName))
                                } else {
                                    jsonChanged = true
                                    DebugLogBuffer.log(logTag, "File physically missing or size mismatch on OTG: ${entry.displayName} (expected size: ${entry.size})")
                                }
                            }
                        }

                        // ── Шаг 3: Поиск новых физических файлов ──
                        val knownNamesAndSizesMap = validJsonEntries.associateBy { (it.displayName.lowercase()) to it.size }
                        val newEntries = mutableListOf<JsonEntry>()

                        for (file in otgFiles) {
                            val name = file.name ?: continue
                            val length = file.length()

                            // Если файл с таким именем и размером уже есть в валидных записях JSON, пропускаем хэширование
                            if (knownNamesAndSizesMap.containsKey(name.lowercase() to length)) {
                                continue
                            }

                            val mime = file.type ?: "image/jpeg"
                            val hash = try { 
                                archiveUtil.calculateSha256(file.uri) 
                            } catch (e: Exception) { 
                                DebugLogBuffer.log(logTag, "Failed to calculate SHA-256 for physical file $name: ${e.localizedMessage}")
                                continue 
                            }

                            val defaultPath = if (mime.startsWith("video/")) "Movies/" else "Pictures/"
                            val entry = JsonEntry(
                                hash = hash,
                                displayName = name,
                                mimeType = mime,
                                size = length,
                                dateModified = file.lastModified() / 1000,
                                originalRelativePath = defaultPath,
                                duration = null,
                                dateArchived = file.lastModified() / 1000
                            )
                            newEntries.add(entry)
                        }

                        val finalJsonEntries = validJsonEntries + newEntries
                        if (newEntries.isNotEmpty()) {
                            jsonChanged = true
                            DebugLogBuffer.log(logTag, "Found ${newEntries.size} new physical files. Registering them...")
                        }

                        // Если метаданные изменились (были удалены мертвые записи или добавлены новые файлы), переписываем JSON
                        if (jsonChanged) {
                            metadataStore.writeMetadata(uri, finalJsonEntries)
                            DebugLogBuffer.log(logTag, "Updated JSON metadata file on OTG drive (total entries: ${finalJsonEntries.size})")
                        }

                        // ── Шаг 4: Синхронизация Room с валидными данными ──
                        val finalHashes = finalJsonEntries.map { it.hash }.toSet()
                        var roomModified = false

                        // Добавляем в Room новые или недостающие записи
                        var insertedToRoom = 0
                        for (entry in finalJsonEntries) {
                            if (db.mediaDao().getById(entry.hash) == null) {
                                val physicalFile = physicalFilesMap[(entry.displayName.lowercase()) to entry.size]
                                val otgFileUri = physicalFile?.uri?.toString() ?: ""
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
                                    dateArchived = entry.dateArchived
                                ))
                                insertedToRoom++
                                roomModified = true
                            }
                        }
                        if (insertedToRoom > 0) {
                            DebugLogBuffer.log(logTag, "Added $insertedToRoom missing entries to Room database")
                        }

                        // Удаляем из Room записи, которых больше нет в финальном списке (файлов больше нет на флешке)
                        val allRoomEntities = db.mediaDao().getAllSync()
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
                            _autoSyncAddedCount.value = newEntries.size
                        }
                        DebugLogBuffer.log(logTag, "Silent sync finished successfully")
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
        scope.launch {
            _syncState.value = "Синхронизация: поиск файлов на OTG..."
            operationMutex.withLock {
                try {
                    val dir = DocumentFile.fromTreeUri(application, uri)
                    if (dir == null || !dir.exists()) throw Exception("Не удалось получить доступ к OTG накопителю")

                    val files = dir.listFiles().filter {
                        !it.isDirectory && it.name != null &&
                        it.name != ".my1drive_uuid" && it.name != ".my1drive_db.json"
                    }
                    if (files.isEmpty()) {
                        _syncState.value = "Синхронизация завершена: файлов нет."
                        return@withLock
                    }

                    var synced = 0; var skipped = 0
                    val logSb = StringBuilder()
                        .appendLine("=== Sync Archive Log ===")
                        .appendLine("Total files found: ${files.size}")

                    // Source of truth: JSON metadata on the OTG drive
                    val jsonEntries = withContext(Dispatchers.IO) {
                        metadataStore.readMetadata(uri)
                    }.toMutableList()

                    val physicalFilesMap = files.associateBy { (it.name?.lowercase() ?: "") to it.length() }
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
                                val actualName = physicalFile.name ?: entry.displayName
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
                            if (file.isDirectory) { logSb.appendLine("Skipping directory: ${file.name}"); continue }
                            val name = file.name ?: continue
                            val length = file.length()

                            // Если файл с таким именем и размером уже есть в JSON, то его хэш и метаданные уже известны.
                            // Проверяем наличие в локальной БД Room, при необходимости восстанавливаем запись.
                            val key = (name.lowercase()) to length
                            val entry = knownNamesAndSizesMap[key]
                            if (entry != null) {
                                logSb.appendLine("Already exists (skipped hash): $name")
                                val otgFileUri = file.uri.toString()
                                if (db.mediaDao().getById(entry.hash) == null) {
                                    db.mediaDao().insert(MediaEntity(
                                        id = entry.hash,
                                        displayName = entry.displayName,
                                        mimeType = entry.mimeType,
                                        size = entry.size,
                                        dateModified = entry.dateModified,
                                        otgUri = otgFileUri,
                                        thumbnailPath = null,
                                        duration = entry.duration,
                                        originalRelativePath = entry.originalRelativePath
                                    ))
                                }
                                continue
                            }

                            _syncState.value = "Обрабатывается: $name (${idx + 1} из ${files.size})"
                            val hash = try { archiveUtil.calculateSha256(file.uri) } catch (e: Exception) {
                                logSb.appendLine("Failed to read/hash $name: ${e.message}"); skipped++; continue
                            }
                            if (hash !in knownHashes) {
                                val mime = file.type ?: "image/jpeg"
                                val defaultPath = if (mime.startsWith("video/")) "Movies/" else "Pictures/"
                                val entry = JsonEntry(
                                    hash = hash,
                                    displayName = name,
                                    mimeType = mime,
                                    size = length,
                                    dateModified = file.lastModified() / 1000,
                                    originalRelativePath = defaultPath,
                                    duration = null,
                                    dateArchived = System.currentTimeMillis() / 1000
                                )
                                newEntries.add(entry)
                                validJsonEntries.add(entry)
                                knownHashes.add(hash)
                                synced++
                                logSb.appendLine("Imported new file: $name (hash=$hash)")
                            } else {
                                logSb.appendLine("Already exists: $name (hash=$hash)")
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
                        // 1. Добавляем в Room новые
                        for (entry in newEntries) {
                            val physicalFile = physicalFilesMap[(entry.displayName.lowercase()) to entry.size]
                            val otgFileUri = physicalFile?.uri?.toString() ?: ""
                            if (db.mediaDao().getById(entry.hash) == null) {
                                db.mediaDao().insert(MediaEntity(
                                    id = entry.hash,
                                    displayName = entry.displayName,
                                    mimeType = entry.mimeType,
                                    size = entry.size,
                                    dateModified = entry.dateModified,
                                    otgUri = otgFileUri,
                                    thumbnailPath = null,
                                    duration = entry.duration,
                                    originalRelativePath = entry.originalRelativePath
                                ))
                            }
                        }
                        
                        // 2. Удаляем из Room пропавшие
                        val allRoomEntities = db.mediaDao().getAllSync()
                        for (entity in allRoomEntities) {
                            if (entity.id !in finalHashes) {
                                entity.thumbnailPath?.let { path ->
                                    val file = File(path)
                                    if (file.exists()) file.delete()
                                }
                                db.mediaDao().delete(entity)
                            }
                        }
                    }

                    repository.refresh()

                    logSb.appendLine("Sync completed: imported $synced, skipped $skipped")
                    _syncState.value = logSb.toString()
                    DebugLogBuffer.log("ManualSync", logSb.toString())
                } catch (e: Exception) {
                    val errorMsg = "Ошибка синхронизации: ${e.localizedMessage}"
                    _syncState.value = errorMsg
                    DebugLogBuffer.log("ManualSync", "Exception in manual sync: ${e.localizedMessage}")
                    val sw = java.io.StringWriter()
                    e.printStackTrace(java.io.PrintWriter(sw))
                    DebugLogBuffer.log("ManualSync", "Stacktrace: $sw")
                } finally {
                    onOperationComplete()
                }
            }
        }
    }

    fun dismissSync() { _syncState.value = null }

    // ─── Archive queue ───

    private val archiveQueue = mutableListOf<Pair<List<MediaItem>, Uri>>()
    private var isArchiveJobRunning = false

    /** Add items to archive queue. If nothing is running, starts immediately. */
    fun startArchiving(items: List<MediaItem>, targetUri: Uri) {
        DebugLogBuffer.log("ArchiveSyncHelper", "startArchiving: items=${items.size}, targetUri=$targetUri, isArchiveJobRunning=$isArchiveJobRunning")
        if (items.isEmpty()) return
        archiveQueue.add(items to targetUri)
        _archiveState.value = _archiveState.value.copy(pendingQueueSize = archiveQueue.size)
        if (!isArchiveJobRunning) {
            isArchiveJobRunning = true
            scope.launch { processArchiveQueue() }
        }
    }

    private suspend fun processArchiveQueue() {
        try {
            while (archiveQueue.isNotEmpty()) {
                val (items, targetUri) = archiveQueue.removeAt(0)
                _archiveState.value = _archiveState.value.copy(pendingQueueSize = archiveQueue.size)
                performArchiving(items, targetUri)
            }
        } finally {
            isArchiveJobRunning = false
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

            for ((index, item) in items.withIndex()) {
                DebugLogBuffer.log(logTag, "Processing queue item [${index + 1}/${items.size}]: ${item.displayName}")
                _archiveState.value = _archiveState.value.copy(
                    currentFileName = item.displayName, currentFileIndex = index + 1, currentStep = ""
                )
                var success: ArchivedInfo? = null
                var itemErr: String? = null
                var isSkipped = false; var skipReason = ""

                archiveUtil.copyAndVerifyItem(item, targetUri).collect { result ->
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
                        DebugLogBuffer.log(logTag, "Item success: ${item.displayName}")
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
            metadataStore.addEntries(otgUri, jsonEntries)
            DebugLogBuffer.log(logTag, "Added entries to JSON metadata on OTG drive")

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
}
