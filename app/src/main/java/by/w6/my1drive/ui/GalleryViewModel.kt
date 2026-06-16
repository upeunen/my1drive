package by.w6.my1drive.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.w6.my1drive.R
import by.w6.my1drive.data.local.AppDatabase
import by.w6.my1drive.data.repository.MediaRepositoryImpl
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.domain.repository.MediaRepository
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
import by.w6.my1drive.utils.ArchiveMetadataStore
import by.w6.my1drive.utils.JsonEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "my1drive_prefs"
private const val PREF_ASK_RESTORE_PATH = "ask_restore_path"
private const val PREF_OTG_URI = "otg_directory_uri"
private const val PREF_KNOWN_ARCHIVE_ID = "known_archive_uuid"
private const val PREF_MISSING_FILES_DISMISSED = "missing_files_dismissed"
private const val PREF_MISSING_FILES_HASH = "missing_files_hash"
private const val IS_LIMIT_ACTIVE = false // Внутренний переключатель лимита 128 МБ (true - включен, false - отключен)
private const val ARCHIVE_SIZE_LIMIT = 128L * 1024 * 1024 // 128 MB

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository: MediaRepository = MediaRepositoryImpl(application, db.mediaDao())
    private val archiveUtil = OtgArchiveUtil(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val previewCache = PreviewCacheManager(application, db.mediaDao())
    private val metadataStore = ArchiveMetadataStore(application)

            val otgManager: OtgConnectionManager by lazy {
        OtgConnectionManager(
            application = application,
            prefs = prefs,
            db = db,
            syncHelper = syncHelper,
            scope = viewModelScope,
            refreshCacheStats = { refreshCacheStats() }
        )
    }

    private val syncHelper: ArchiveSyncHelper by lazy {
        ArchiveSyncHelper(
            application = application,
            db = db,
            repository = repository,
            archiveUtil = archiveUtil,
            prefs = prefs,
            previewCache = previewCache,
            scope = viewModelScope,
            onOperationComplete = { otgManager.updateArchiveSize() },
            onArchiveSuccess = { items ->
                _selectedIds.value = emptySet()
            }
        )
    }

    // ─── Flows ───

    val mediaItems: StateFlow<List<MediaItem>> = repository.getMediaItemsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groupedMediaItems: StateFlow<List<GalleryItem>> = mediaItems
        .map { list ->
            val localItems = list.filter { it.status == MediaStatus.ON_DEVICE }
            val resultList = mutableListOf<GalleryItem>()
            for ((headerText, items) in localItems.groupBy { formatDateHeader(it.dateModified) }) {
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
            for ((headerText, items) in archivedList.groupBy { formatDateHeader(it.dateModified) }) {
                resultList.add(GalleryItem.Header(headerText))
                items.forEach { resultList.add(GalleryItem.Media(it)) }
            }
            resultList
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

        private val _otgDirectoryUri = MutableStateFlow<Uri?>(null)
    val otgDirectoryUri = _otgDirectoryUri.asStateFlow()

    private val _isOtgConnected = MutableStateFlow(false)
    val isOtgConnected: StateFlow<Boolean> = _isOtgConnected.asStateFlow()

    val archiveState: StateFlow<ArchiveState> = syncHelper.archiveState
    val restoreState = MutableStateFlow(RestoreState())
    val syncState: StateFlow<String?> = syncHelper.syncState

    private val _showLimitReachedDialog = MutableStateFlow(false)
    val showLimitReachedDialog = _showLimitReachedDialog.asStateFlow()

    val isLimitActive = IS_LIMIT_ACTIVE

    private val _physicalArchiveSize = MutableStateFlow(0L)
    val physicalArchiveSize = _physicalArchiveSize.asStateFlow()

    private val _askRestorePath = MutableStateFlow(prefs.getBoolean(PREF_ASK_RESTORE_PATH, false))
    val askRestorePath = _askRestorePath.asStateFlow()

    private val _restoreRequest = MutableStateFlow<RestoreRequest?>(null)
    val restoreRequest = _restoreRequest.asStateFlow()

    val missingFilesNotification: StateFlow<List<String>?> = syncHelper.missingFilesNotification
    val autoSyncAddedCount: StateFlow<Int> = syncHelper.autoSyncAddedCount

    private var pendingRestoreItems: List<MediaItem> = emptyList()
    var isSilentSyncing get() = syncHelper.isSilentSyncing; set(v) { syncHelper.isSilentSyncing = v }

    private val _cacheStats = MutableStateFlow(Pair(0L, 0))
    val cacheStats = _cacheStats.asStateFlow()

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog = _showCreateFolderDialog.asStateFlow()

        // ─── Init ───

    init {
        // Restore saved OTG URI
        val savedUri = prefs.getString(PREF_OTG_URI, null)?.let {
            try { Uri.parse(it) } catch (e: Exception) {
                prefs.edit().remove(PREF_OTG_URI).apply()
                null
            }
        }
        if (savedUri != null) {
            _otgDirectoryUri.value = savedUri
        }

        // Subscribe to otgManager flows
        viewModelScope.launch {
            otgManager.otgDirectoryUri.collect { uri ->
                _otgDirectoryUri.value = uri
            }
        }

        viewModelScope.launch {
            otgManager.archiveSize.collect { size ->
                _physicalArchiveSize.value = size
            }
        }

                // Subscribe to status changes for isOtgConnected
        viewModelScope.launch {
            otgManager.status.collect { status ->
                _isOtgConnected.value = status == DriveStatus.KNOWN_DRIVE_CONNECTED
            }
        }

        // Start the polling loop
        otgManager.start(savedUri)
    }

        fun updateOtgStatus() {
        otgManager.onPhysicalConnectionChanged()
    }

    fun dismissFirstLaunchDialog() {
        otgManager.dismissFirstLaunchDialog()
    }

    fun dismissUnknownDriveDialog() {
        otgManager.dismissUnknownDriveDialog()
    }

    fun createNewArchive() {
        otgManager.createNewArchive()
    }

    // ─── Preview cache ───

    fun onPreviewCached(hash: String, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.mediaDao().updateLastAccessed(hash, System.currentTimeMillis())
            db.mediaDao().getById(hash)?.let { if (it.thumbnailPath != path) db.mediaDao().insert(it.copy(thumbnailPath = path)) }
            previewCache.evictIfNeeded(); refreshCacheStats()
        }
    }

    fun refreshCacheStats() { viewModelScope.launch(Dispatchers.IO) { _cacheStats.value = Pair(previewCache.getCacheSize(), previewCache.getCacheFileCount()) } }
    fun clearPreviewCache() { viewModelScope.launch { previewCache.clearAll(); refreshCacheStats(); repository.refresh() } }
    fun getCacheMaxMb(): Long = previewCache.getMaxBytes() / (1024 * 1024)
    fun getPreviewCacheManager(): PreviewCacheManager = previewCache
    fun setCacheMaxMb(mb: Long) { previewCache.setMaxBytes(mb * 1024 * 1024); viewModelScope.launch { previewCache.evictIfNeeded() } }

        // ─── OTG folder ───

    fun setOtgDirectory(uri: Uri) {
        otgManager.onOtgUriSelected(uri)
    }

    fun ejectOtg() {
        otgManager.onEject()
    }

    // ─── Selection ───

    fun toggleSelection(itemId: String) { _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (contains(itemId)) remove(itemId) else add(itemId) } }
    fun clearSelection() { _selectedIds.value = emptySet() }

    // ─── Sync & Archive delegated ───

    fun silentSyncArchive() { syncHelper.silentSyncArchive(otgManager.otgDirectoryUri.value) }
    fun dismissMissingFilesNotification() { syncHelper.dismissMissingFilesNotification() }
    fun dismissAutoSyncAddedCount() { syncHelper.dismissAutoSyncAddedCount() }
    fun syncArchive() { syncHelper.syncArchive(otgManager.otgDirectoryUri.value) }
    fun dismissSync() { syncHelper.dismissSync() }
    fun startArchiving(targetUri: Uri) {
        val selected = mediaItems.value.filter { it.id in _selectedIds.value }
        if (selected.isEmpty()) return

        if (IS_LIMIT_ACTIVE) {
            val currentArchivedSize = physicalArchiveSize.value
            val newSelectionSize = selected.sumOf { it.size }
            val totalProjectedSize = currentArchivedSize + newSelectionSize
            if (totalProjectedSize > ARCHIVE_SIZE_LIMIT) {
                _showLimitReachedDialog.value = true
                return
            }
        }
        syncHelper.startArchiving(selected, targetUri)
        // Размер архива пересчитается в колбэке onOperationComplete
    }

    fun archiveSingleItem(item: MediaItem, targetUri: Uri) {
        if (IS_LIMIT_ACTIVE) {
            val currentArchivedSize = physicalArchiveSize.value
            val totalProjectedSize = currentArchivedSize + item.size

            if (totalProjectedSize > ARCHIVE_SIZE_LIMIT) {
                _showLimitReachedDialog.value = true
                return
            }
        }
        syncHelper.startArchiving(listOf(item), targetUri)
        // Размер архива пересчитается в колбэке onOperationComplete
    }
    fun restoreSingleItem(item: MediaItem) {
        startRestoring(listOf(item), null)
    }
    fun dismissError() { syncHelper.dismissError() }
    fun refresh() { repository.refresh() }

    // ─── Device delete sender (for system dialog on Q+) ───

    private val _deviceDeleteSender = MutableStateFlow<IntentSender?>(null)
    val deviceDeleteSender: StateFlow<IntentSender?> = _deviceDeleteSender.asStateFlow()

    private val _deviceDeletePendingItems = mutableListOf<MediaItem>()

    // ─── Delete state ───

    private val _pendingDelete = MutableStateFlow<List<MediaItem>?>(null)
    val pendingDelete: StateFlow<List<MediaItem>?> = _pendingDelete.asStateFlow()

    fun requestDeleteSelected() {
        val selected = mediaItems.value.filter { it.id in _selectedIds.value }
        if (selected.isNotEmpty()) {
            _pendingDelete.value = selected
        }
    }

    fun confirmDelete() {
        val items = _pendingDelete.value ?: return
        _pendingDelete.value = null
        _selectedIds.value = emptySet()
        deleteDeviceItems(items.filter { it.status == MediaStatus.ON_DEVICE })
        deleteArchivedItems(items.filter { it.status == MediaStatus.ARCHIVED_OTG })
    }

    fun dismissDelete() { _pendingDelete.value = null }

    /** Called when user confirms device delete in system dialog */
    fun onDeviceDeleteConfirmed() {
        val items = _deviceDeletePendingItems.toList()
        _deviceDeleteSender.value = null
        _deviceDeletePendingItems.clear()
        if (items.isEmpty()) return
        directDeleteDeviceItems(items)
    }

    /** Called when user cancels device delete in system dialog */
    fun onDeviceDeleteCancelled() {
        _deviceDeleteSender.value = null
        _deviceDeletePendingItems.clear()
    }

    /** Unified device delete: works on all API levels */
    private fun deleteDeviceItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri })
                _deviceDeleteSender.value = pendingIntent.intentSender
                _deviceDeletePendingItems.clear()
                _deviceDeletePendingItems.addAll(items)
            } catch (_: Exception) {
                directDeleteDeviceItems(items)
            }
        } else {
            directDeleteDeviceItems(items)
        }
    }

    private fun directDeleteDeviceItems(items: List<MediaItem>) {
        val context = getApplication<Application>()
        val remaining = items.toMutableList()
        for (item in items) {
            try {
                context.contentResolver.delete(item.uri, null, null)
                remaining.remove(item)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    _deviceDeleteSender.value = e.userAction.actionIntent.intentSender
                    _deviceDeletePendingItems.clear()
                    _deviceDeletePendingItems.addAll(remaining)
                    return
                }
            } catch (_: Exception) { }
        }
        _deviceDeletePendingItems.clear()
        viewModelScope.launch { repository.refresh() }
    }

        private fun deleteArchivedItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val otgUri = otgManager.otgDirectoryUri.value
            for (item in items) {
                try {
                    // 1. Remove from JSON metadata on OTG drive (source of truth)
                    if (otgUri != null && item.hash != null) {
                        metadataStore.removeEntry(otgUri, item.hash)
                    }
                    // 2. Delete physical file from OTG drive
                    item.otgUri?.let { fileUri ->
                        try { DocumentFile.fromSingleUri(getApplication(), Uri.parse(fileUri))?.delete() } catch (_: Exception) { }
                    }
                    // 3. Remove from Room (local cache)
                    repository.deleteArchivedItem(item)
                } catch (_: Exception) { }
            }
            repository.refresh()
            otgManager.updateArchiveSize()
        }
    }

    // ─── Restore ───

    fun setAskRestorePath(value: Boolean) { _askRestorePath.value = value; prefs.edit().putBoolean(PREF_ASK_RESTORE_PATH, value).apply() }

    fun requestRestore() {
        val selected = mediaItems.value.filter { it.id in _selectedIds.value && it.status == MediaStatus.ARCHIVED_OTG }
        if (selected.isEmpty()) return
        startRestoring(selected, null)
    }

    fun restoreToOriginalPath() { pendingRestoreItems.toList().let { pendingRestoreItems = emptyList(); _restoreRequest.value = null; startRestoring(it, null) } }
    fun restoreToChosenFolder(uri: Uri) { pendingRestoreItems.toList().let { pendingRestoreItems = emptyList(); _restoreRequest.value = null; startRestoring(it, uri) } }
    fun dismissRestoreRequest() { pendingRestoreItems = emptyList(); _restoreRequest.value = null }

        private fun startRestoring(items: List<MediaItem>, targetDirUri: Uri?) {
        viewModelScope.launch {
            var successCount = 0; val errors = mutableListOf<String>()
            val otgUri = otgManager.otgDirectoryUri.value
            restoreState.value = RestoreState(isRestoring = true, totalFiles = items.size)
            for ((index, item) in items.withIndex()) {
                restoreState.value = restoreState.value.copy(currentFileName = item.displayName, currentFileIndex = index + 1, currentStep = "")
                archiveUtil.restoreItem(item, targetDirUri).collect { result ->
                    when (result) {
                        is by.w6.my1drive.utils.RestoreResult.Progress -> restoreState.value = restoreState.value.copy(
                            currentStep = result.step, progressFraction = (index.toFloat() + result.progressFraction) / items.size
                        )
                        is by.w6.my1drive.utils.RestoreResult.Success -> {
                            successCount++
                            try {
                                // 1. Remove from JSON metadata on OTG drive (source of truth)
                                if (otgUri != null && result.item.hash != null) {
                                    metadataStore.removeEntry(otgUri, result.item.hash)
                                }
                                // 2. Remove from Room (local cache)
                                repository.deleteArchivedItem(result.item)
                            } catch (_: Exception) { }
                        }
                        is by.w6.my1drive.utils.RestoreResult.Error -> errors.add("${result.displayName}: ${result.message}")
                    }
                }
            }
            _selectedIds.value = emptySet(); repository.refresh()
                        restoreState.value = RestoreState(isRestoring = false, successCount = successCount, error = when {
                errors.isNotEmpty() -> "Восстановлено: $successCount из ${items.size}.\n\nОшибки:\n" + errors.joinToString("\n")
                successCount < items.size -> "Восстановлено: $successCount из ${items.size}."
                else -> null
            })
            otgManager.updateArchiveSize()
        }
    }

    fun dismissRestoreError() { restoreState.value = restoreState.value.copy(error = null) }
    fun dismissArchiveError() { syncHelper.dismissError() }

    // ─── Deferred Delete (fullscreen preview) ───

    private val _deferredDeleteIds = MutableStateFlow<Set<String>>(emptySet())
    val deferredDeleteIds: StateFlow<Set<String>> = _deferredDeleteIds.asStateFlow()

    fun toggleDeferredDelete(itemId: String) {
        _deferredDeleteIds.value = _deferredDeleteIds.value.toMutableSet().apply {
            if (contains(itemId)) remove(itemId) else add(itemId)
        }
    }

    /** Called when fullscreen preview is closed — deletes all deferred items */
    fun commitDeferredDeletes() {
        val itemsToDelete = mediaItems.value.filter { it.id in _deferredDeleteIds.value }
        if (itemsToDelete.isEmpty()) return
        _deferredDeleteIds.value = emptySet()
        deleteDeviceItems(itemsToDelete.filter { it.status == MediaStatus.ON_DEVICE })
        deleteArchivedItems(itemsToDelete.filter { it.status == MediaStatus.ARCHIVED_OTG })
    }

    fun clearDeferredDeletes() {
        _deferredDeleteIds.value = emptySet()
    }

    // ─── Create folder ───

    fun requestCreateFolder() { _showCreateFolderDialog.value = true }
    fun dismissCreateFolderDialog() { _showCreateFolderDialog.value = false }

        fun createFolderOnOtg(folderName: String) {
        otgManager.otgDirectoryUri.value?.let { uri ->
            viewModelScope.launch(Dispatchers.IO) { try { DocumentFile.fromTreeUri(getApplication(), uri)?.createDirectory(folderName) } catch (_: Exception) { } }
        }
        _showCreateFolderDialog.value = false
    }

    // ─── Helpers ───

    private fun formatDateHeader(dateSeconds: Long): String {
        val dateMs = dateSeconds * 1000; val now = Calendar.getInstance(); val tc = Calendar.getInstance().apply { timeInMillis = dateMs }
        val ctx = getApplication<Application>()
        return when {
            DateUtils.isToday(dateMs) -> ctx.getString(R.string.date_today)
            isYesterday(tc, now) -> ctx.getString(R.string.date_yesterday)
            tc.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(dateMs))
            else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(dateMs))
        }
    }

        fun getOtgDirectoryDisplayName(): String? {
        val uri = otgManager.otgDirectoryUri.value ?: return null
        val context = getApplication<Application>()
        return try {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            docFile?.name ?: Uri.decode(uri.toString().substringAfterLast("/"))
        } catch (e: Exception) {
            Uri.decode(uri.toString().substringAfterLast("/"))
        }
    }

        fun isOtgLocalFolder(): Boolean {
        val uri = otgManager.otgDirectoryUri.value ?: return false
        val path = uri.path ?: return false
        val treeSegment = path.substringAfter("/tree/", "")
        if (treeSegment.isEmpty()) return false
        val rawId = treeSegment.substringBefore(":")
        return rawId.equals("primary", ignoreCase = true)
    }

    private fun isYesterday(target: Calendar, now: Calendar): Boolean {
        val yesterday = now.clone() as Calendar; yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    }

    fun dismissLimitReachedDialog() { _showLimitReachedDialog.value = false }

    fun getArchivedSize(): Long {
        return physicalArchiveSize.value
    }
}
