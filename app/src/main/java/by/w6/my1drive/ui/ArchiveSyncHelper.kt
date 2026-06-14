package by.w6.my1drive.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.data.local.AppDatabase
import by.w6.my1drive.data.local.MediaEntity
import by.w6.my1drive.data.repository.MediaRepositoryImpl
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.domain.repository.MediaRepository
import by.w6.my1drive.utils.CopyVerifyResult
import by.w6.my1drive.utils.OtgArchiveUtil
import by.w6.my1drive.utils.PreviewCacheManager
import by.w6.my1drive.utils.RestoreResult
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
    private val onOperationComplete: () -> Unit = {}
) {

    private val _syncState = MutableStateFlow<String?>(null)
    val syncState: StateFlow<String?> = _syncState.asStateFlow()

    private val _archiveState = MutableStateFlow(ArchiveState())
    val archiveState: StateFlow<ArchiveState> = _archiveState.asStateFlow()

    private val _pendingDeleteRequest = MutableStateFlow<PendingDeleteRequest?>(null)
    val pendingDeleteRequest: StateFlow<PendingDeleteRequest?> = _pendingDeleteRequest.asStateFlow()

    private val _missingFilesNotification = MutableStateFlow<List<String>?>(null)
    val missingFilesNotification: StateFlow<List<String>?> = _missingFilesNotification.asStateFlow()

    private val _autoSyncAddedCount = MutableStateFlow(0)
    val autoSyncAddedCount: StateFlow<Int> = _autoSyncAddedCount.asStateFlow()

    val pendingDeletesQueue = mutableListOf<ArchivedInfo>()
    var lastArchiveSummary: String? = null
    var isSilentSyncing = false

    private val PREF_MISSING_FILES_DISMISSED = "missing_files_dismissed"
    private val PREF_MISSING_FILES_HASH = "missing_files_hash"

    // ─── Silent auto-sync ───

    fun silentSyncArchive(otgDirectoryUri: Uri?) {
        val uri = otgDirectoryUri ?: return
        isSilentSyncing = true
        scope.launch(Dispatchers.IO) {
            try {
                val dir = DocumentFile.fromTreeUri(application, uri) ?: return@launch
                if (!dir.exists()) return@launch

                val otgFiles = dir.listFiles().filter {
                    !it.isDirectory && it.name != null && it.name != ".my1drive_uuid"
                }

                var addedCount = 0
                for (file in otgFiles) {
                    val name = file.name ?: continue
                    val mime = file.type ?: "image/jpeg"
                    val hash = try { archiveUtil.calculateSha256(file.uri) } catch (_: Exception) { continue }
                    if (db.mediaDao().getById(hash) == null) {
                        db.mediaDao().insert(MediaEntity(
                            id = hash, displayName = name, mimeType = mime,
                            size = file.length(), dateModified = file.lastModified() / 1000,
                            otgUri = file.uri.toString(), thumbnailPath = null,
                            duration = null, originalRelativePath = null
                        ))
                        addedCount++
                    }
                }

                if (addedCount > 0) { repository.refresh(); _autoSyncAddedCount.value = addedCount }
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

    fun syncArchive(otgDirectoryUri: Uri?) {
        val uri = otgDirectoryUri ?: return
        scope.launch {
            _syncState.value = "Синхронизация: поиск файлов на OTG..."
            try {
                val dir = DocumentFile.fromTreeUri(application, uri)
                if (dir == null || !dir.exists()) throw Exception("Не удалось получить доступ к OTG накопителю")

                val files = dir.listFiles()
                if (files.isEmpty()) { _syncState.value = "Синхронизация завершена: файлов нет."; return@launch }

                var synced = 0; var skipped = 0
                val logSb = StringBuilder().appendLine("=== Sync Archive Log ===").appendLine("Total files found: ${files.size}")

                withContext(Dispatchers.IO) {
                    for ((idx, file) in files.withIndex()) {
                        if (file.isDirectory) { logSb.appendLine("Skipping directory: ${file.name}"); continue }
                        _syncState.value = "Обрабатывается: ${file.name} (${idx + 1} из ${files.size})"
                        val hash = try { archiveUtil.calculateSha256(file.uri) } catch (e: Exception) {
                            logSb.appendLine("Failed to read/hash ${file.name}: ${e.message}"); skipped++; continue
                        }
                        if (db.mediaDao().getById(hash) == null) {
                            db.mediaDao().insert(MediaEntity(
                                id = hash, displayName = file.name!!, mimeType = file.type ?: "image/jpeg",
                                size = file.length(), dateModified = file.lastModified() / 1000,
                                otgUri = file.uri.toString(), thumbnailPath = null,
                                duration = null, originalRelativePath = null
                            ))
                            synced++; logSb.appendLine("Imported new file: ${file.name} (hash=$hash)")
                        } else logSb.appendLine("Already exists: ${file.name} (hash=$hash)")
                    }
                }
                logSb.appendLine("Sync completed: imported $synced, skipped $skipped")
                _syncState.value = logSb.toString()
                repository.refresh()
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
        _archiveState.value = ArchiveState(isArchiving = true, totalFiles = items.size, pendingQueueSize = archiveQueue.size)
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

        lastArchiveSummary = if (skipped.isNotEmpty() || errorMsg != null) buildString {
            append("Некоторые файлы не удалось архивировать.\n")
            append("Успешно скопировано: ${copied.size} из ${items.size}.\n")
            if (skipped.isNotEmpty()) append("Пропущено: ${skipped.size}\n") else append("\n")
            if (errorMsg != null) append("\nОшибка:\n$errorMsg")
        } else null

        if (copied.isNotEmpty()) processDeletions(copied)
        else {
            _archiveState.value = ArchiveState(isArchiving = false, error = lastArchiveSummary ?: "Ошибка архивирования", pendingQueueSize = archiveQueue.size)
            lastArchiveSummary = null
            onOperationComplete()
        }
    }

    private fun processDeletions(list: List<ArchivedInfo>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(application.contentResolver, list.map { it.item.uri })
                _pendingDeleteRequest.value = PendingDeleteRequest(
                    intentSender = pendingIntent.intentSender,
                    items = list.map { it.item }, hashes = list.map { it.hash },
                    otgUris = list.map { it.otgUri }, thumbnailPaths = list.map { it.thumbnailPath }
                )
                        } catch (e: Exception) {
                _archiveState.value = _archiveState.value.copy(isArchiving = false, error = e.localizedMessage, pendingQueueSize = archiveQueue.size)
                onOperationComplete()
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            pendingDeletesQueue.clear(); pendingDeletesQueue.addAll(list); processNextQueueDelete()
        } else {
            scope.launch {
                try {
                    for (info in list) {
                        application.contentResolver.delete(info.item.uri, null, null)
                        repository.insertArchivedItem(info.item, info.otgUri, info.hash, info.thumbnailPath, info.item.originalRelativePath)
                    }
                    _archiveState.value = ArchiveState(isArchiving = false, pendingQueueSize = archiveQueue.size)
                } catch (e: Exception) {
                    _archiveState.value = _archiveState.value.copy(isArchiving = false, error = e.localizedMessage, pendingQueueSize = archiveQueue.size)
                } finally {
                    onOperationComplete()
                }
            }
        }
    }

    private fun processNextQueueDelete() {
        if (pendingDeletesQueue.isEmpty()) {
            _archiveState.value = ArchiveState(isArchiving = false, error = lastArchiveSummary, pendingQueueSize = archiveQueue.size)
            lastArchiveSummary = null
            onOperationComplete()
            return
        }
        val next = pendingDeletesQueue.first()
        try {
            if (application.contentResolver.delete(next.item.uri, null, null) > 0) {
                scope.launch {
                    repository.insertArchivedItem(next.item, next.otgUri, next.hash, next.thumbnailPath, next.item.originalRelativePath)
                    pendingDeletesQueue.removeAt(0); processNextQueueDelete()
                }
            } else {
                _archiveState.value = _archiveState.value.copy(isArchiving = false, error = "delete_failed", pendingQueueSize = archiveQueue.size)
                onOperationComplete()
            }
        } catch (se: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && se is RecoverableSecurityException) {
                _pendingDeleteRequest.value = PendingDeleteRequest(
                    intentSender = se.userAction.actionIntent.intentSender,
                    items = listOf(next.item), hashes = listOf(next.hash),
                    otgUris = listOf(next.otgUri), thumbnailPaths = listOf(next.thumbnailPath)
                )
            } else {
                _archiveState.value = _archiveState.value.copy(isArchiving = false, error = se.localizedMessage, pendingQueueSize = archiveQueue.size)
                onOperationComplete()
            }
        }
    }

    fun onDeletePermissionGranted(selectedIds: MutableStateFlow<Set<String>>) {
        val pending = _pendingDeleteRequest.value ?: return
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    for (i in pending.items.indices) {
                        try { application.contentResolver.delete(pending.items[i].uri, null, null) } catch (_: Exception) { }
                        try { repository.insertArchivedItem(pending.items[i], pending.otgUris[i], pending.hashes[i], pending.thumbnailPaths[i], pending.items[i].originalRelativePath) } catch (_: Exception) { }
                    }
                                        selectedIds.value = emptySet()
                    _pendingDeleteRequest.value = null
                    _archiveState.value = ArchiveState(isArchiving = false, error = lastArchiveSummary, pendingQueueSize = archiveQueue.size)
                    lastArchiveSummary = null
                    onOperationComplete()
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    val next = pendingDeletesQueue.firstOrNull()
                    if (next != null) {
                        application.contentResolver.delete(next.item.uri, null, null)
                        repository.insertArchivedItem(next.item, next.otgUri, next.hash, next.thumbnailPath, next.item.originalRelativePath)
                        selectedIds.value = selectedIds.value.toMutableSet().apply { remove(next.item.id) }
                        pendingDeletesQueue.removeAt(0)
                    }
                    _pendingDeleteRequest.value = null; processNextQueueDelete()
                }
                        } catch (e: Exception) {
                _archiveState.value = _archiveState.value.copy(isArchiving = false, error = e.localizedMessage, pendingQueueSize = archiveQueue.size)
                _pendingDeleteRequest.value = null
                onOperationComplete()
            }
        }
    }

    fun dismissPendingDelete(selectedIds: MutableStateFlow<Set<String>>) {
        _pendingDeleteRequest.value = null; pendingDeletesQueue.clear()
        _archiveState.value = ArchiveState(isArchiving = false, error = lastArchiveSummary, pendingQueueSize = archiveQueue.size); lastArchiveSummary = null
        onOperationComplete()
    }

    fun dismissError() { _archiveState.value = _archiveState.value.copy(error = null) }
}
