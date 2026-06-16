package by.w6.my1drive.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.data.local.AppDatabase
import by.w6.my1drive.data.local.MediaEntity
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.repository.MediaRepository
import by.w6.my1drive.utils.ArchiveMetadataStore
import by.w6.my1drive.utils.CopyVerifyResult
import by.w6.my1drive.utils.JsonEntry
import by.w6.my1drive.utils.OtgArchiveUtil
import by.w6.my1drive.utils.PreviewCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        scope.launch(Dispatchers.IO) {
            try {
                val dir = DocumentFile.fromTreeUri(application, uri) ?: return@launch
                if (!dir.exists()) return@launch

                // ── Шаг 1: Синхронизация JSON → Room ──
                // JSON — источник истины. Все записи из JSON должны быть в Room.
                val jsonEntries = metadataStore.readMetadata(uri)
                var roomModified = false
                for (entry in jsonEntries) {
                    if (db.mediaDao().getById(entry.hash) == null) {
                        // Поищем актуальный URI файла на флешке
                        val otgFileUri = dir.findFile(entry.displayName)?.uri?.toString() ?: ""
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
                        roomModified = true
                    }
                }

                // ── Шаг 2: Поиск новых файлов на флешке ──
                val knownHashes = jsonEntries.map { it.hash }.toHashSet()

                val otgFiles = dir.listFiles().filter {
                    !it.isDirectory && it.name != null &&
                    it.name != ".my1drive_uuid" && it.name != ".my1drive_db.json"
                }

                val newEntries = mutableListOf<JsonEntry>()
                for (file in otgFiles) {
                    val name = file.name ?: continue
                    val mime = file.type ?: "image/jpeg"
                    val hash = try { archiveUtil.calculateSha256(file.uri) } catch (_: Exception) { continue }

                    if (hash !in knownHashes) {
                        val defaultPath = if (mime.startsWith("video/")) "Movies/" else "Pictures/"
                        val entry = JsonEntry(
                            hash = hash,
                            displayName = name,
                            mimeType = mime,
                            size = file.length(),
                            dateModified = file.lastModified() / 1000,
                            originalRelativePath = defaultPath,
                            duration = null
                        )
                        newEntries.add(entry)
                        knownHashes.add(hash)
                    }
                }

                if (newEntries.isNotEmpty()) {
                    // Записать новые записи в JSON (источник истины)
                    val updatedJson = jsonEntries.toMutableList().apply { addAll(newEntries) }
                    metadataStore.writeMetadata(uri, updatedJson)

                    // Добавить в Room
                    for (entry in newEntries) {
                        val otgFileUri = dir.findFile(entry.displayName)?.uri?.toString() ?: ""
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
                    roomModified = true
                }

                // ── Шаг 3: Удаление мёртвых записей (файлов, пропавших с OTG) ──
                                val allRoomEntities = db.mediaDao().getAllSync()
                                for (entity in allRoomEntities) {
                                    if (entity.otgUri.isNullOrEmpty()) {
                                        // Нет URI — не можем проверить, но чистим на всякий случай
                                        db.mediaDao().delete(entity)
                                        roomModified = true
                                        continue
                                    }
                                    try {
                                        val docFile = DocumentFile.fromSingleUri(application, Uri.parse(entity.otgUri))
                                        if (docFile == null || !docFile.exists()) {
                                            // Файла нет на флешке — удаляем запись из Room
                                            entity.thumbnailPath?.let { path ->
                                                val file = java.io.File(path)
                                                if (file.exists()) file.delete()
                                            }
                                            db.mediaDao().delete(entity)
                                            roomModified = true
                                        }
                                    } catch (_: Exception) {
                                        // Ошибка проверки — удаляем на всякий случай
                                        db.mediaDao().delete(entity)
                                        roomModified = true
                                    }
                                }

                                if (roomModified) {
                                    repository.refresh()
                                    _autoSyncAddedCount.value = newEntries.size
                                }
                            } catch (_: Exception) { } finally { isSilentSyncing = false; onOperationComplete() }
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
            try {
                val dir = DocumentFile.fromTreeUri(application, uri)
                if (dir == null || !dir.exists()) throw Exception("Не удалось получить доступ к OTG накопителю")

                val files = dir.listFiles().filter {
                    !it.isDirectory && it.name != null &&
                    it.name != ".my1drive_uuid" && it.name != ".my1drive_db.json"
                }
                if (files.isEmpty()) { _syncState.value = "Синхронизация завершена: файлов нет."; return@launch }

                var synced = 0; var skipped = 0
                val logSb = StringBuilder()
                    .appendLine("=== Sync Archive Log ===")
                    .appendLine("Total files found: ${files.size}")

                // Source of truth: JSON metadata on the OTG drive
                val jsonEntries = withContext(Dispatchers.IO) {
                    metadataStore.readMetadata(uri)
                }.toMutableList()
                val knownHashes = jsonEntries.map { it.hash }.toHashSet()
                val newEntries = mutableListOf<JsonEntry>()

                withContext(Dispatchers.IO) {
                    for ((idx, file) in files.withIndex()) {
                        if (file.isDirectory) { logSb.appendLine("Skipping directory: ${file.name}"); continue }
                        _syncState.value = "Обрабатывается: ${file.name} (${idx + 1} из ${files.size})"
                        val hash = try { archiveUtil.calculateSha256(file.uri) } catch (e: Exception) {
                            logSb.appendLine("Failed to read/hash ${file.name}: ${e.message}"); skipped++; continue
                        }
                        if (hash !in knownHashes) {
                            val mime = file.type ?: "image/jpeg"
                            val defaultPath = if (mime.startsWith("video/")) "Movies/" else "Pictures/"
                            val entry = JsonEntry(
                                hash = hash,
                                displayName = file.name!!,
                                mimeType = mime,
                                size = file.length(),
                                dateModified = file.lastModified() / 1000,
                                originalRelativePath = defaultPath,
                                duration = null
                            )
                            newEntries.add(entry)
                            jsonEntries.add(entry)
                            knownHashes.add(hash)
                            synced++
                            logSb.appendLine("Imported new file: ${file.name} (hash=$hash)")
                        } else {
                            logSb.appendLine("Already exists: ${file.name} (hash=$hash)")
                        }
                    }
                }

                if (newEntries.isNotEmpty()) {
                    // 1. Write truth to JSON on OTG drive
                    withContext(Dispatchers.IO) { metadataStore.writeMetadata(uri, jsonEntries) }

                    // 2. Sync Room cache on device
                    withContext(Dispatchers.IO) {
                        for (entry in newEntries) {
                            val otgFileUri = dir.findFile(entry.displayName)?.uri?.toString() ?: ""
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
                    repository.refresh()
                }

                logSb.appendLine("Sync completed: imported $synced, skipped $skipped")
                _syncState.value = logSb.toString()
            } catch (e: Exception) {
                _syncState.value = "Ошибка синхронизации: ${e.localizedMessage}"
            } finally {
                onOperationComplete()
            }
        }
    }

    fun dismissSync() { _syncState.value = null }

    // ─── Archive queue ───

    private val archiveQueue = mutableListOf<Pair<List<MediaItem>, Uri>>()
    private var isArchiveJobRunning = false

    /** Add items to archive queue. If nothing is running, starts immediately. */
    fun startArchiving(items: List<MediaItem>, targetUri: Uri) {
        if (items.isEmpty()) return
        archiveQueue.add(items to targetUri)
        _archiveState.value = _archiveState.value.copy(pendingQueueSize = archiveQueue.size)
        if (!isArchiveJobRunning) {
            isArchiveJobRunning = true
            scope.launch { processArchiveQueue() }
        }
    }

    private suspend fun processArchiveQueue() {
        while (archiveQueue.isNotEmpty()) {
            val (items, targetUri) = archiveQueue.removeAt(0)
            _archiveState.value = _archiveState.value.copy(pendingQueueSize = archiveQueue.size)
            performArchiving(items, targetUri)
        }
        isArchiveJobRunning = false
    }

    private suspend fun performArchiving(items: List<MediaItem>, targetUri: Uri) {
        if (items.isEmpty()) return
        _archiveState.value = ArchiveState(
            isArchiving = true, totalFiles = items.size,
            pendingQueueSize = archiveQueue.size
        )
        val copied = mutableListOf<ArchivedInfo>()
        val skipped = mutableListOf<Pair<MediaItem, String>>()
        var errorMsg: String? = null

        for ((index, item) in items.withIndex()) {
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
                success != null -> copied.add(success!!)
                isSkipped -> skipped.add(item to skipReason)
                itemErr != null -> errorMsg = itemErr
            }
        }

                val skippedFiles = skipped.map { (item, reason) -> item.displayName to reason }

        if (copied.isNotEmpty()) {
            processArchivedResults(copied, targetUri)
            // Уведомить ViewModel об успешно заархивированных файлах
            onArchiveSuccess(copied.map { it.item })
        } else {
            _archiveState.value = ArchiveState(
                isArchiving = false, error = "Ошибка архивирования",
                skippedFiles = skippedFiles,
                pendingQueueSize = archiveQueue.size
            )
            onOperationComplete()
        }
    }

    /**
     * Process successfully archived files:
     * 1. Add entry to JSON metadata on the OTG drive (source of truth)
     * 2. Insert into Room (local cache)
     */
    private suspend fun processArchivedResults(list: List<ArchivedInfo>, otgUri: Uri) {
        try {
            // 1. Write truth to JSON on the OTG drive
            val jsonEntries = list.map { info ->
                JsonEntry(
                    hash = info.hash,
                    displayName = info.item.displayName,
                    mimeType = info.item.mimeType,
                    size = info.item.size,
                    dateModified = info.item.dateModified,
                    originalRelativePath = info.item.originalRelativePath,
                    duration = info.item.duration
                )
            }
            metadataStore.addEntries(otgUri, jsonEntries)

            // 2. Insert into Room (local cache)
            for (info in list) {
                repository.insertArchivedItem(
                    info.item, info.otgUri, info.hash,
                    info.thumbnailPath, info.item.originalRelativePath
                )
            }

            _archiveState.value = ArchiveState(
                isArchiving = false, pendingQueueSize = archiveQueue.size
            )
        } catch (e: Exception) {
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
