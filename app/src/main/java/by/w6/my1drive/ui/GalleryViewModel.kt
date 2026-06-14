package by.w6.my1drive.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.w6.my1drive.R
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PendingDeleteRequest(
    val intentSender: android.content.IntentSender,
    val items: List<MediaItem>,
    val hashes: List<String>,
    val otgUris: List<String>,
    val thumbnailPaths: List<String?>
)

data class ArchiveState(
    val isArchiving: Boolean = false,
    val currentFileName: String = "",
    val currentStep: String = "",
    val progressFraction: Float = 0f,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val error: String? = null
)

data class RestoreState(
    val isRestoring: Boolean = false,
    val currentFileName: String = "",
    val currentStep: String = "",
    val progressFraction: Float = 0f,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val successCount: Int = 0,
    val error: String? = null
)

data class ArchivedInfo(
    val item: MediaItem,
    val hash: String,
    val otgUri: String,
    val thumbnailPath: String?
)

private const val PREFS_NAME = "my1drive_prefs"
private const val PREF_OTG_URI = "otg_directory_uri"
private const val PREF_MISSING_FILES_DISMISSED = "missing_files_dismissed"
private const val PREF_MISSING_FILES_HASH = "missing_files_hash"

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository: MediaRepository = MediaRepositoryImpl(application, db.mediaDao())
    private val archiveUtil = OtgArchiveUtil(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val previewCache = PreviewCacheManager(application, db.mediaDao())

    val mediaItems: StateFlow<List<MediaItem>> = repository.getMediaItemsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groupedMediaItems: StateFlow<List<GalleryItem>> = mediaItems
        .map { list ->
            val localItems = list.filter { it.status == MediaStatus.ON_DEVICE }
            val resultList = mutableListOf<GalleryItem>()
            val groupedMap = localItems.groupBy { formatDateHeader(it.dateModified) }
            for ((headerText, items) in groupedMap) {
                resultList.add(GalleryItem.Header(headerText))
                items.forEach { resultList.add(GalleryItem.Media(it)) }
            }
            resultList
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val archivedGroupedItems: StateFlow<List<GalleryItem>> = mediaItems
        .map { list ->
            val archivedList = list.filter { it.status == MediaStatus.ARCHIVED_OTG && (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")) }
            val resultList = mutableListOf<GalleryItem>()
            val groupedMap = archivedList.groupBy { formatDateHeader(it.dateModified) }
            for ((headerText, items) in groupedMap) {
                resultList.add(GalleryItem.Header(headerText))
                items.forEach { resultList.add(GalleryItem.Media(it)) }
            }
            resultList
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    private val _archiveState = MutableStateFlow(ArchiveState())
    val archiveState = _archiveState.asStateFlow()

    private val _restoreState = MutableStateFlow(RestoreState())
    val restoreState = _restoreState.asStateFlow()

    private val _pendingDeleteRequest = MutableStateFlow<PendingDeleteRequest?>(null)
    val pendingDeleteRequest = _pendingDeleteRequest.asStateFlow()

    private val _otgDirectoryUri = MutableStateFlow<Uri?>(null)
    val otgDirectoryUri = _otgDirectoryUri.asStateFlow()

    private val _isOtgConnected = MutableStateFlow(false)
    val isOtgConnected = _isOtgConnected.asStateFlow()

    private val _missingFilesNotification = MutableStateFlow<List<String>?>(null)
    val missingFilesNotification = _missingFilesNotification.asStateFlow()

    private val _autoSyncAddedCount = MutableStateFlow(0)
    val autoSyncAddedCount = _autoSyncAddedCount.asStateFlow()

    private val _showRestorePicker = MutableStateFlow(false)
    val showRestorePicker = _showRestorePicker.asStateFlow()

    private val pendingDeletesQueue = mutableListOf<ArchivedInfo>()

    private var isSilentSyncing = false

    private val _cacheStats = MutableStateFlow(Pair(0L, 0))
    val cacheStats = _cacheStats.asStateFlow()

    init {
        val savedUriStr = prefs.getString(PREF_OTG_URI, null)
        if (savedUriStr != null) {
            try {
                _otgDirectoryUri.value = Uri.parse(savedUriStr)
            } catch (e: Exception) {
                prefs.edit().remove(PREF_OTG_URI).apply()
            }
        }
        viewModelScope.launch {
            var previousConnected: Boolean? = null
            while (true) {
                val connected = withContext(Dispatchers.IO) { computeIsConnected() }
                if (connected != _isOtgConnected.value) {
                    _isOtgConnected.value = connected
                }

                if (connected && previousConnected != true &&
                    !_archiveState.value.isArchiving &&
                    !_restoreState.value.isRestoring &&
                    !isSilentSyncing
                ) {
                    silentSyncArchive()
                    refreshCacheStats()
                }

                if (connected && previousConnected == null) {
                    withContext(Dispatchers.IO) { previewCache.migrateOldThumbnails() }
                }

                previousConnected = connected
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    private suspend fun computeIsConnected(): Boolean {
        val context = getApplication<Application>()
        val savedUri = _otgDirectoryUri.value ?: return false
        return try {
            val docFile = DocumentFile.fromTreeUri(context, savedUri)
            docFile != null && docFile.exists() && docFile.canRead()
        } catch (e: Exception) {
            false
        }
    }

    fun updateOtgStatus() {
        viewModelScope.launch {
            _isOtgConnected.value = withContext(Dispatchers.IO) { computeIsConnected() }
        }
    }

    fun onPreviewCached(hash: String, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.mediaDao().updateLastAccessed(hash, System.currentTimeMillis())
            val entity = db.mediaDao().getById(hash)
            if (entity != null && entity.thumbnailPath != path) {
                db.mediaDao().insert(entity.copy(thumbnailPath = path))
            }
            previewCache.evictIfNeeded()
            refreshCacheStats()
        }
    }

    fun refreshCacheStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheStats.value = Pair(previewCache.getCacheSize(), previewCache.getCacheFileCount())
        }
    }

    fun clearPreviewCache() {
        viewModelScope.launch {
            previewCache.clearAll()
            refreshCacheStats()
            repository.refresh()
        }
    }

    fun getCacheMaxMb(): Long = previewCache.getMaxBytes() / (1024 * 1024)

    fun getPreviewCacheManager(): PreviewCacheManager = previewCache

    fun setOtgDirectory(uri: Uri) {
        _otgDirectoryUri.value = uri
        prefs.edit().putString(PREF_OTG_URI, uri.toString()).apply()
        viewModelScope.launch {
            _isOtgConnected.value = withContext(Dispatchers.IO) { computeIsConnected() }
        }
    }

    fun toggleSelection(itemId: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(itemId)) remove(itemId) else add(itemId)
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    private fun silentSyncArchive() {
        val uri = _otgDirectoryUri.value ?: return
        isSilentSyncing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val dir = DocumentFile.fromTreeUri(context, uri)
                if (dir == null || !dir.exists()) return@launch

                val otgFiles = dir.listFiles().filter {
                    !it.isDirectory && it.name != null && it.name != ".my1drive_uuid"
                }

                var addedCount = 0
                for (file in otgFiles) {
                    val name = file.name ?: continue
                    val mime = file.type ?: "image/jpeg"
                    val hash = try {
                        archiveUtil.calculateSha256(file.uri)
                    } catch (e: Exception) {
                        continue
                    }
                    val existing = db.mediaDao().getById(hash)
                    if (existing == null) {
                        val entity = MediaEntity(
                            id = hash,
                            displayName = name,
                            mimeType = mime,
                            size = file.length(),
                            dateModified = file.lastModified() / 1000,
                            otgUri = file.uri.toString(),
                            thumbnailPath = null,
                            duration = null,
                            originalRelativePath = null
                        )
                        db.mediaDao().insert(entity)
                        addedCount++
                    }
                }

                val otgUriStrings = otgFiles.map { it.uri.toString() }.toSet()
                val dbEntities = db.mediaDao().getAllSync()
                val missingNames = dbEntities
                    .filter { it.otgUri !in otgUriStrings }
                    .map { it.displayName }

                if (addedCount > 0) {
                    repository.refresh()
                    _autoSyncAddedCount.value = addedCount
                }

                if (missingNames.isNotEmpty()) {
                    val missingHash = missingNames.sorted().joinToString(",")
                    val wasDismissed = prefs.getBoolean(PREF_MISSING_FILES_DISMISSED, false)
                    val lastHash = prefs.getString(PREF_MISSING_FILES_HASH, null)
                    if (!wasDismissed || lastHash != missingHash) {
                        _missingFilesNotification.value = missingNames
                    }
                }
            } catch (e: Exception) {
            } finally {
                isSilentSyncing = false
            }
        }
    }

    fun dismissMissingFilesNotification() {
        val names = _missingFilesNotification.value
        if (names != null) {
            val hash = names.sorted().joinToString(",")
            prefs.edit()
                .putBoolean(PREF_MISSING_FILES_DISMISSED, true)
                .putString(PREF_MISSING_FILES_HASH, hash)
                .apply()
        }
        _missingFilesNotification.value = null
    }

    fun dismissAutoSyncAddedCount() {
        _autoSyncAddedCount.value = 0
    }

    fun startArchiving() {
        val targetUri = _otgDirectoryUri.value ?: return
        val selectedItems = mediaItems.value.filter { it.id in _selectedIds.value }
        if (selectedItems.isEmpty()) return
        performArchiving(selectedItems, targetUri)
    }

    private fun performArchiving(items: List<MediaItem>, targetUri: Uri) {
        if (items.isEmpty()) return

        viewModelScope.launch {
            _archiveState.value = ArchiveState(isArchiving = true, totalFiles = items.size)

            val successfullyCopied = mutableListOf<ArchivedInfo>()
            var errorMsg: String? = null

            for ((index, item) in items.withIndex()) {
                _archiveState.value = _archiveState.value.copy(
                    currentFileName = item.displayName,
                    currentFileIndex = index + 1,
                    currentStep = ""
                )

                var successInfo: ArchivedInfo? = null
                var itemError: String? = null

                archiveUtil.copyAndVerifyItem(item, targetUri).collect { result ->
                    when (result) {
                        is CopyVerifyResult.Progress -> {
                            val overallProgress = (index.toFloat() + result.progressFraction) / items.size
                            _archiveState.value = _archiveState.value.copy(
                                currentStep = result.step,
                                progressFraction = overallProgress
                            )
                        }
                        is CopyVerifyResult.Success -> {
                            successInfo = ArchivedInfo(result.item, result.hash, result.otgUri, result.thumbnailPath)
                        }
                        is CopyVerifyResult.Skipped -> { }
                        is CopyVerifyResult.Error -> {
                            itemError = result.message
                        }
                    }
                }

                if (successInfo != null) successfullyCopied.add(successInfo!!)
                else if (itemError != null) errorMsg = itemError
            }

            if (successfullyCopied.isNotEmpty()) {
                processDeletions(successfullyCopied)
            } else {
                _archiveState.value = ArchiveState(
                    isArchiving = false,
                    error = errorMsg ?: "No files were copied"
                )
            }
        }
    }

    private fun processDeletions(list: List<ArchivedInfo>) {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val uris = list.map { it.item.uri }
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                _pendingDeleteRequest.value = PendingDeleteRequest(
                    intentSender = pendingIntent.intentSender,
                    items = list.map { it.item },
                    hashes = list.map { it.hash },
                    otgUris = list.map { it.otgUri },
                    thumbnailPaths = list.map { it.thumbnailPath }
                )
            } catch (e: Exception) {
                _archiveState.value = _archiveState.value.copy(isArchiving = false, error = e.localizedMessage)
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            pendingDeletesQueue.clear()
            pendingDeletesQueue.addAll(list)
            processNextQueueDelete()
        } else {
            viewModelScope.launch {
                try {
                    for (info in list) {
                        context.contentResolver.delete(info.item.uri, null, null)
                        repository.insertArchivedItem(info.item, info.otgUri, info.hash, info.thumbnailPath, info.item.originalRelativePath)
                    }
                    _selectedIds.value = emptySet()
                    _archiveState.value = ArchiveState(isArchiving = false)
                } catch (e: Exception) {
                    _archiveState.value = _archiveState.value.copy(isArchiving = false, error = e.localizedMessage)
                }
            }
        }
    }

    private fun processNextQueueDelete() {
        if (pendingDeletesQueue.isEmpty()) {
            _archiveState.value = ArchiveState(isArchiving = false)
            _selectedIds.value = emptySet()
            return
        }
        val next = pendingDeletesQueue.first()
        val context = getApplication<Application>()
        try {
            val deleted = context.contentResolver.delete(next.item.uri, null, null)
            if (deleted > 0) {
                viewModelScope.launch {
                    repository.insertArchivedItem(next.item, next.otgUri, next.hash, next.thumbnailPath, next.item.originalRelativePath)
                    _selectedIds.value = _selectedIds.value.toMutableSet().apply { remove(next.item.id) }
                    pendingDeletesQueue.removeAt(0)
                    processNextQueueDelete()
                }
            } else {
                _archiveState.value = _archiveState.value.copy(isArchiving = false, error = "delete_failed")
            }
        } catch (securityException: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is RecoverableSecurityException) {
                _pendingDeleteRequest.value = PendingDeleteRequest(
                    intentSender = securityException.userAction.actionIntent.intentSender,
                    items = listOf(next.item),
                    hashes = listOf(next.hash),
                    otgUris = listOf(next.otgUri),
                    thumbnailPaths = listOf(next.thumbnailPath)
                )
            } else {
                _archiveState.value = _archiveState.value.copy(isArchiving = false, error = securityException.localizedMessage)
            }
        }
    }

    fun onDeletePermissionGranted() {
        val pending = _pendingDeleteRequest.value ?: return
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    for (i in pending.items.indices) {
                        val item = pending.items[i]
                        try { context.contentResolver.delete(item.uri, null, null) } catch (e: Exception) { e.printStackTrace() }
                        try {
                            repository.insertArchivedItem(item, pending.otgUris[i], pending.hashes[i], pending.thumbnailPaths[i], item.originalRelativePath)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    _selectedIds.value = emptySet()
                    _pendingDeleteRequest.value = null
                    _archiveState.value = ArchiveState(isArchiving = false)
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    val next = pendingDeletesQueue.firstOrNull()
                    if (next != null) {
                        context.contentResolver.delete(next.item.uri, null, null)
                        repository.insertArchivedItem(next.item, next.otgUri, next.hash, next.thumbnailPath, next.item.originalRelativePath)
                        _selectedIds.value = _selectedIds.value.toMutableSet().apply { remove(next.item.id) }
                        pendingDeletesQueue.removeAt(0)
                    }
                    _pendingDeleteRequest.value = null
                    processNextQueueDelete()
                }
            } catch (e: Exception) {
                _archiveState.value = _archiveState.value.copy(isArchiving = false, error = e.localizedMessage)
                _pendingDeleteRequest.value = null
            }
        }
    }

    fun dismissPendingDelete() {
        _pendingDeleteRequest.value = null
        pendingDeletesQueue.clear()
        _archiveState.value = ArchiveState(isArchiving = false)
    }

    fun dismissError() {
        _archiveState.value = _archiveState.value.copy(error = null)
    }

    fun refresh() {
        repository.refresh()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = _selectedIds.value.toList()
            for (id in ids) {
                try {
                    val item = mediaItems.value.find { it.id == id } ?: continue
                    repository.deleteArchivedItem(item)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _selectedIds.value = emptySet()
            repository.refresh()
        }
    }

    fun deleteArchivedRecord(item: MediaItem) {
        viewModelScope.launch {
            try {
                repository.deleteArchivedItem(item)
                _selectedIds.value = _selectedIds.value.toMutableSet().apply { remove(item.id) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun requestRestore() {
        val selectedItems = mediaItems.value.filter { it.id in _selectedIds.value && it.status == MediaStatus.ARCHIVED_OTG }
        if (selectedItems.isEmpty()) return

        val allHavePath = selectedItems.all { it.originalRelativePath != null }
        if (allHavePath) {
            startRestoring(selectedItems, targetDirUri = null)
        } else {
            _showRestorePicker.value = true
        }
    }

    fun restoreToChosenFolder(folderUri: Uri) {
        val items = mediaItems.value.filter { it.id in _selectedIds.value && it.status == MediaStatus.ARCHIVED_OTG }
        _showRestorePicker.value = false
        startRestoring(items, targetDirUri = folderUri)
    }

    fun dismissRestorePicker() {
        _showRestorePicker.value = false
    }

    private fun startRestoring(items: List<MediaItem>, targetDirUri: Uri?) {
        viewModelScope.launch {
            _restoreState.value = RestoreState(isRestoring = true, totalFiles = items.size)
            var successCount = 0
            val errors = mutableListOf<String>()

            for ((index, item) in items.withIndex()) {
                _restoreState.value = _restoreState.value.copy(
                    currentFileName = item.displayName,
                    currentFileIndex = index + 1,
                    currentStep = ""
                )
                archiveUtil.restoreItem(item, targetDirUri).collect { result ->
                    when (result) {
                        is RestoreResult.Progress -> {
                            val overallProgress = (index.toFloat() + result.progressFraction) / items.size
                            _restoreState.value = _restoreState.value.copy(currentStep = result.step, progressFraction = overallProgress)
                        }
                        is RestoreResult.Success -> {
                            successCount++
                            try { repository.deleteArchivedItem(result.item) } catch (e: Exception) { e.printStackTrace() }
                        }
                        is RestoreResult.Error -> errors.add(": ")
                    }
                }
            }

            _selectedIds.value = emptySet()
            repository.refresh()

            val errorSummary = when {
                errors.isNotEmpty() -> "Restored:  of .\n\nErrors:\n" + errors.joinToString("\n")
                successCount < items.size -> "Restored:  of ."
                else -> null
            }
            _restoreState.value = RestoreState(isRestoring = false, successCount = successCount, error = errorSummary)
        }
    }

    fun dismissRestoreError() {
        _restoreState.value = _restoreState.value.copy(error = null)
    }

    private fun formatDateHeader(dateSeconds: Long): String {
        val dateMs = dateSeconds * 1000
        val now = Calendar.getInstance()
        val timeCalendar = Calendar.getInstance().apply { timeInMillis = dateMs }
        val context = getApplication<Application>()
        return when {
            DateUtils.isToday(dateMs) -> context.getString(R.string.date_today)
            isYesterday(timeCalendar, now) -> context.getString(R.string.date_yesterday)
            timeCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
                SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(dateMs))
            else ->
                SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(dateMs))
        }
    }

    private fun isYesterday(target: Calendar, now: Calendar): Boolean {
        val yesterday = now.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    }
}
