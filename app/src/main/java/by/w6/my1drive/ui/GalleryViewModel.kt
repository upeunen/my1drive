package by.w6.my1drive.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import by.w6.my1drive.R
import by.w6.my1drive.data.local.AppDatabase
import by.w6.my1drive.data.repository.MediaRepositoryImpl
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.domain.repository.MediaRepository
import by.w6.my1drive.utils.DebugLogBuffer
import by.w6.my1drive.utils.OtgArchiveUtil
import by.w6.my1drive.utils.PreviewCacheManager
import by.w6.my1drive.utils.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOn
import by.w6.my1drive.utils.ArchiveMetadataStore
import by.w6.my1drive.utils.JsonEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.core.content.FileProvider
import java.io.File

private const val PREFS_NAME = "my1drive_prefs"
private const val PREF_ASK_RESTORE_PATH = "ask_restore_path"
private const val PREF_OTG_URI = "otg_directory_uri"
private const val PREF_DEVICE_URI = "device_directory_uri"
private const val PREF_KNOWN_ARCHIVE_ID = "known_archive_uuid"
private const val PREF_MISSING_FILES_DISMISSED = "missing_files_dismissed"
private const val PREF_MISSING_FILES_HASH = "missing_files_hash"
private const val PREF_LOCAL_FOLDER_SKIP_COUNT = "local_folder_skip_count"
private const val PREF_LAST_REQUESTED_FOLDER = "last_requested_folder_path"
private const val IS_LIMIT_ACTIVE = false // Внутренний переключатель лимита 128 МБ (true - включен, false - отключен)
private const val ARCHIVE_SIZE_LIMIT = 128L * 1024 * 1024 // 128 MB

enum class ArchiveSortMode {
    BY_PHOTO_DATE,
    BY_ARCHIVE_DATE
}

enum class DeviceSortMode {
    BY_PHOTO_DATE,
    BY_RESTORE_DATE
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository: MediaRepository = MediaRepositoryImpl(application, db.mediaDao())
    private val archiveUtil = OtgArchiveUtil(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val previewCache = PreviewCacheManager(application, db.mediaDao())
    private val metadataStore = ArchiveMetadataStore(application)
    val vpsManager = by.w6.my1drive.utils.VpsConnectionManager(application)

    /** Список всех известных архивов (носителей) из БД — для легенды цветов в настройках */
    val knownArchives = db.archiveDao().getAllFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _dialogState = kotlinx.coroutines.flow.MutableStateFlow(DialogState())
    val dialogState = _dialogState.asStateFlow()

    val limitRepository = by.w6.my1drive.data.local.LimitRepository(application)
    val photosArchivedCount = limitRepository.photosArchivedCountFlow
    val videosArchivedCount = limitRepository.videosArchivedCountFlow
    val isPremiumUnlocked = limitRepository.isPremiumUnlockedFlow

    fun showCreateArchiveGuideDialog(uri: Uri) { _dialogState.update { it.copy(showCreateArchiveGuideDialog = uri) } }
    fun dismissCreateArchiveGuideDialog() { _dialogState.update { it.copy(showCreateArchiveGuideDialog = null) } }
    
    fun showPaywall() { _dialogState.update { it.copy(showPaywall = true) } }
    fun dismissPaywall() { _dialogState.update { it.copy(showPaywall = false) } }

    val otgManager: OtgConnectionManager by lazy {
        OtgConnectionManager(
            application = application,
            prefs = prefs,
            db = db,
            syncHelper = syncHelper,
            scope = viewModelScope,
            refreshCacheStats = { refreshCacheStats() },
            isBusy = {
                syncHelper.archiveState.value.isArchiving || restoreState.value.isRestoring
            },
            onShowFirstLaunchDialog = { v -> _dialogState.update { it.copy(showFirstLaunchDialog = v) } },
            onShowUnknownDriveDialog = { v -> _dialogState.update { it.copy(showUnknownDriveDialog = v) } },
            onShowUnreadableOtgDialog = { v -> _dialogState.update { it.copy(showUnreadableOtgDialog = v) } },
            onShowWriteProtectedRootDialog = { v -> _dialogState.update { it.copy(showWriteProtectedRootDialog = v) } },
            onShowLocalFolderDialog = { v -> _dialogState.update { it.copy(showLocalFolderDialog = v) } },
            onShowNamingDialog = { v -> _dialogState.update { it.copy(showNamingDialog = v) } },
            onShowCreateArchiveGuideDialog = { v -> _dialogState.update { it.copy(showCreateArchiveGuideDialog = v) } }
        )
    }

    fun dismissFirstLaunchDialog() { _dialogState.update { it.copy(showFirstLaunchDialog = false) } }
    fun dismissUnknownDriveDialog() { _dialogState.update { it.copy(showUnknownDriveDialog = false) } }
    fun dismissUnreadableOtgDialog() { _dialogState.update { it.copy(showUnreadableOtgDialog = false) } }
    fun dismissWriteProtectedRootDialog() { _dialogState.update { it.copy(showWriteProtectedRootDialog = false) } }
    fun dismissLocalFolderDialog() { _dialogState.update { it.copy(showLocalFolderDialog = false) } }
    fun dismissNamingDialog() { _dialogState.update { it.copy(showNamingDialog = null) } }
    fun triggerWriteProtectedRootDialog() { _dialogState.update { it.copy(showWriteProtectedRootDialog = true) } }
    fun showNamingDialog(uri: Uri) { _dialogState.update { it.copy(showNamingDialog = uri) } }

    private val archiveInteractor: ArchiveInteractor by lazy {
        ArchiveInteractor(
            application = application,
            repository = repository,
            otgManager = otgManager,
            metadataStore = metadataStore,
            archiveUtil = archiveUtil,
            scope = viewModelScope,
            restoreState = restoreState,
            restoringItemIds = _restoringItemIds,
            onItemDeselected = { id -> selectionManager.deselectItems(listOf(id)) }
        )
    }

    private val syncHelper: ArchiveSyncHelper
        get() = ArchiveSyncHelper.getInstance(getApplication())

    init {
        viewModelScope.launch {
            syncHelper.operationCompleteEvent.collect {
                otgManager.updateArchiveSize()
                otgManager.setCheckingConnection(false)
            }
        }
        
        viewModelScope.launch {
            syncHelper.archiveSuccessEvent.collect { items ->
                mediaOperationInteractor.startDeletingWithPermissionCheck(items)
            }
        }
        
        viewModelScope.launch {
            syncHelper.itemArchivedEvent.collect { item ->
                selectionManager.deselectItems(listOf(item.id))
            }
        }
        
        viewModelScope.launch {
            syncHelper.previewCachedEvent.collect { (hash, path) ->
                onPreviewCached(hash, path)
            }
        }
    }


    fun onPause() {
        otgManager.pausePolling()
    }

    fun onResume() {
        otgManager.resumePolling()
    }

    // ─── Flows ───

    val mediaItems: StateFlow<List<MediaItem>> = repository.getMediaItemsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val displayManager = GalleryDisplayManager(application, prefs, viewModelScope, mediaItems)
    
    val deviceSortMode = displayManager.deviceSortMode
    fun setDeviceSortMode(mode: DeviceSortMode) { displayManager.setDeviceSortMode(mode) }

    val gridColumnsCount = displayManager.gridColumnsCount
    fun setGridColumnsCount(count: Int) { displayManager.setGridColumnsCount(count) }

    val groupedMediaItems = displayManager.groupedMediaItems

    val archiveSortMode = displayManager.archiveSortMode
    fun setArchiveSortMode(mode: ArchiveSortMode) { displayManager.setArchiveSortMode(mode) }

    val archivedGroupedItems = displayManager.archivedGroupedItems

    val selectionManager = SelectionManager()
    val selectedIds = selectionManager.selectedIds

    private val _restoringItemIds = MutableStateFlow<Set<String>>(emptySet())
    val restoringItemIds = _restoringItemIds.asStateFlow()

        private val _otgDirectoryUri = MutableStateFlow<Uri?>(null)
    val otgDirectoryUri = _otgDirectoryUri.asStateFlow()

    private val _deviceDirectoryUri = MutableStateFlow<Uri?>(null)
    val deviceDirectoryUri = _deviceDirectoryUri.asStateFlow()

    private val _pendingDeviceFolderToRequest = MutableStateFlow<String?>(null)
    val pendingDeviceFolderToRequest = _pendingDeviceFolderToRequest.asStateFlow()

    private val _isOtgConnected = MutableStateFlow(false)
    val isOtgConnected: StateFlow<Boolean> = _isOtgConnected.asStateFlow()

    val mediaOperationInteractor: MediaOperationInteractor by lazy {
        MediaOperationInteractor(
            application = application,
            repository = repository,
            scope = viewModelScope,
            otgManager = otgManager,
            archiveInteractor = archiveInteractor,
            isOtgConnected = isOtgConnected,
            onArchiveTaskReady = { items, targetUri ->
                syncHelper.startArchiving(items, targetUri)
            }
        )
    }
    val archiveState: StateFlow<ArchiveState> = syncHelper.archiveState
    val archivingItemIds: StateFlow<Set<String>> = syncHelper.archivingItemIds
    val copiedItemIds: StateFlow<Set<String>> = syncHelper.copiedItemIds
    val restoreState = MutableStateFlow(RestoreState())
    private var restoringJob: kotlinx.coroutines.Job? = null
    private var isRestoreCancellationRequested = false
    val syncState: StateFlow<String?> = syncHelper.syncState
    val syncProgressState: StateFlow<SyncProgressState> = syncHelper.syncProgressState

    private val _showLimitReachedDialog = MutableStateFlow(false)
    val showLimitReachedDialog = _showLimitReachedDialog.asStateFlow()

    val showEjectSuccessDialog: StateFlow<Boolean> = otgManager.showEjectSuccessDialog
    val isEjecting: StateFlow<Boolean> = otgManager.isEjecting

    val isLimitActive = IS_LIMIT_ACTIVE

    private val _physicalArchiveSize = MutableStateFlow(0L)
    val physicalArchiveSize = _physicalArchiveSize.asStateFlow()

    private val _isScrolling = MutableStateFlow(false)
    val isScrolling = _isScrolling.asStateFlow()
    fun setScrolling(scrolling: Boolean) {
        _isScrolling.value = scrolling
    }

    private val _askRestorePath = MutableStateFlow(prefs.getBoolean(PREF_ASK_RESTORE_PATH, false))
    val askRestorePath = _askRestorePath.asStateFlow()

    private val _restoreRequest = MutableStateFlow<RestoreRequest?>(null)
    val restoreRequest = _restoreRequest.asStateFlow()

    val missingFilesNotification: StateFlow<List<String>?> = syncHelper.missingFilesNotification
    val autoSyncAddedCount: StateFlow<Int> = syncHelper.autoSyncAddedCount

    private var pendingRestoreItems: List<MediaItem> = emptyList()
    var isSilentSyncing get() = syncHelper.isSilentSyncing; set(v) { syncHelper.isSilentSyncing = v }
    val isSilentSyncingFlow: StateFlow<Boolean> = syncHelper.isSilentSyncingFlow

    private val _cacheStats = MutableStateFlow(Pair(0L, 0))
    val cacheStats = _cacheStats.asStateFlow()

    private val _isStorageLow = MutableStateFlow(false)
    val isStorageLow = _isStorageLow.asStateFlow()

    val isSharingPreparing = mediaOperationInteractor.isSharingPreparing

    val showCreateFolderDialog = mediaOperationInteractor.showCreateFolderDialog

    private var pendingArchiveTask: Pair<List<MediaItem>, Uri>? = null




    fun onFolderPermissionGranted(uri: Uri) {
        if (mediaOperationInteractor.missingFoldersQueue.isNotEmpty()) {
            val folder = mediaOperationInteractor.missingFoldersQueue.removeAt(0)
            DebugLogBuffer.log("GalleryViewModel", "Permission granted for folder: $folder. Remaining: ${mediaOperationInteractor.missingFoldersQueue.size}")
            mediaOperationInteractor.requestNextFolderPermission()
        }
    }

    fun onFolderPermissionCancelled() {
        mediaOperationInteractor.missingFoldersQueue.clear()
        mediaOperationInteractor.pendingArchiveTask = null
        mediaOperationInteractor.pendingDeleteTask = null
        _showArchiveFolderAccessDialog.value = false
        _archiveAccessFolderPath.value = null
        DebugLogBuffer.log("GalleryViewModel", "Folder permission request cancelled. Aborting task.")
    }


        // ─── Archive folder access dialog ───

        private val _showArchiveFolderAccessDialog = MutableStateFlow(false)
        val showArchiveFolderAccessDialog: StateFlow<Boolean> = _showArchiveFolderAccessDialog.asStateFlow()

        private val _archiveAccessFolderPath = MutableStateFlow<String?>(null)
        val archiveAccessFolderPath: StateFlow<String?> = _archiveAccessFolderPath.asStateFlow()

                fun showArchiveFolderAccessDialog(folderPath: String) {
            _archiveAccessFolderPath.value = folderPath
            _pendingDeviceFolderToRequest.value = folderPath
            _showArchiveFolderAccessDialog.value = true
        }

        fun dismissArchiveFolderAccessDialog() {
            onFolderPermissionCancelled()
        }

        fun confirmArchiveFolderAccess() {
            _showArchiveFolderAccessDialog.value = false
            // После подтверждения откроется SAF через коллбэк в MainActivity
        }

        // ─── Thumbnail sync state ───

    val thumbnailManager = ThumbnailSyncManager(
        db = db,
        syncHelper = syncHelper,
        scope = viewModelScope,
        activeArchiveUuidFlow = otgManager.activeArchiveUuid,
        isOtgConnectedFlow = _isOtgConnected,
        isScrollingFlow = _isScrolling,
        refreshCacheStats = { refreshCacheStats() }
    )

    val isSyncingThumbnails = thumbnailManager.isSyncingThumbnails
    val syncThumbnailsProgress = thumbnailManager.syncThumbnailsProgress
    val missingThumbnailsCount = thumbnailManager.missingThumbnailsCount


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

        // Restore saved Device URI
        val savedDeviceUri = prefs.getString(PREF_DEVICE_URI, null)?.let {
            try { Uri.parse(it) } catch (e: Exception) {
                prefs.edit().remove(PREF_DEVICE_URI).apply()
                null
            }
        }
        if (savedDeviceUri != null) {
            _deviceDirectoryUri.value = savedDeviceUri
        }

        // Subscribe to otgManager flows
        viewModelScope.launch {
            otgManager.otgDirectoryUri.collect { uri ->
                _otgDirectoryUri.value = uri
            }
        }

        viewModelScope.launch {
            otgManager.deviceDirectoryUri.collect { uri ->
                _deviceDirectoryUri.value = uri
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

        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                otgManager.activeArchiveUuid,
                _isOtgConnected,
                _isScrolling
            ) { uuid, connected, scrolling ->
                Triple(uuid, connected, scrolling)
            }.collect { (uuid, connected, scrolling) ->
                updateMissingThumbnailsCount()
                if (connected && uuid != null && !scrolling) {
                    startSilentThumbnailSync()
                } else {
                    thumbnailManager.cancelSilentThumbnailSync()
                }
            }
        }

        // Start the polling loop
        otgManager.start(savedUri, savedDeviceUri)

        // Clean up temporary shared folder on start
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sharedTempDir = File(application.cacheDir, "shared_temp")
                if (sharedTempDir.exists() && sharedTempDir.isDirectory) {
                    sharedTempDir.deleteRecursively()
                }
            } catch (e: Exception) {
                by.w6.my1drive.utils.DebugLogBuffer.log("GalleryViewModel", "Cache stats update failed: ${e.message}")
            }
        }
    }

    fun updateOtgStatus(isStartup: Boolean = false) {
        otgManager.onPhysicalConnectionChanged(isStartup)
    }


    fun setDeviceDirectory(uri: Uri) {
        otgManager.onDeviceUriSelected(uri)
        onFolderPermissionGranted(uri)
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

    fun refreshCacheStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheStats.value = Pair(previewCache.getCacheSize(), previewCache.getCacheFileCount())
            _isStorageLow.value = checkIsStorageLow()
        }
    }

    private fun checkIsStorageLow(): Boolean {
        return try {
            val context = getApplication<Application>()
            val stat = android.os.StatFs(context.filesDir.absolutePath)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            bytesAvailable < 500L * 1024 * 1024
        } catch (_: Exception) {
            false
        }
    }
    fun clearPreviewCache() {
        viewModelScope.launch {
            otgManager.onEject() // Safe eject/unmount to stop silent sync
            withContext(Dispatchers.IO) {
                previewCache.clearAll()
                db.mediaDao().deleteAll()
            }
            otgManager.resetActiveArchiveUuid()
            refreshCacheStats()
            repository.refresh()
        }
    }

    fun getMissingThumbnailsCount(): Int = thumbnailManager.missingThumbnailsCount.value

    fun updateMissingThumbnailsCount() {
        thumbnailManager.updateMissingThumbnailsCount()
    }

    fun startThumbnailSync() {
        thumbnailManager.startThumbnailSync()
    }

    fun cancelThumbnailSync() {
        thumbnailManager.cancelThumbnailSync()
    }

    fun startSilentThumbnailSync() {
        thumbnailManager.startSilentThumbnailSync()
    }

    fun deleteArchive(uuid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val itemsToDelete = db.mediaDao().getAllSync().filter { it.archiveUuid == uuid }
            itemsToDelete.forEach { entity ->
                entity.thumbnailPath?.let { path ->
                    val file = java.io.File(path)
                    if (file.exists()) file.delete()
                }
            }
            db.archiveDao().delete(uuid)
            db.mediaDao().deleteByArchiveUuid(uuid)
            withContext(Dispatchers.Main) {
                repository.refresh()
            }
        }
    }
    fun getCacheMaxMb(): Long = previewCache.getMaxBytes() / (1024 * 1024)
    fun getPreviewCacheManager(): PreviewCacheManager = previewCache
    fun setCacheMaxMb(mb: Long) { previewCache.setMaxBytes(mb * 1024 * 1024); viewModelScope.launch { previewCache.evictIfNeeded() } }

        // ─── OTG folder ───


    fun setOtgDirectory(uri: Uri) {
        otgManager.onOtgUriSelected(uri)
        val context = getApplication<Application>()
        if (!mediaOperationInteractor.hasPermissionForFolder(context, "DCIM")) {
            _pendingDeviceFolderToRequest.value = "DCIM"
            otgManager.showLocalFolderPrompt()
        }
    }

    fun ejectOtg() {
        // Остановить синхронизацию миниатюр перед извлечением
        cancelThumbnailSync()
        thumbnailManager.resetProgress()
        otgManager.onEject()
    }

    fun cancelArchiving() {
        syncHelper.cancelArchiving()
    }

    fun cancelRestoring() {
        archiveInteractor.cancelRestore()
    }

    // ─── Selection ───

    fun toggleSelection(itemId: String) { selectionManager.toggleSelection(itemId) }
    fun clearSelection() { selectionManager.clearSelection() }
    fun selectItems(itemIds: Collection<String>) { selectionManager.selectItems(itemIds) }
    fun deselectItems(itemIds: Collection<String>) { selectionManager.deselectItems(itemIds) }

    // ─── Sync & Archive delegated ───

    fun isVpsEnabled(): Boolean = vpsManager.isVpsEnabled()
    fun silentSyncArchive() { syncHelper.silentSyncArchive(otgManager.otgDirectoryUri.value) }
    fun dismissMissingFilesNotification() { syncHelper.dismissMissingFilesNotification() }
    fun dismissAutoSyncAddedCount() { syncHelper.dismissAutoSyncAddedCount() }
    fun syncArchive() { syncHelper.syncArchive(otgManager.otgDirectoryUri.value) }
    fun dismissSync() { syncHelper.dismissSync() }
    fun startArchiving(targetUri: Uri) {
        val selected = mediaItems.value.filter { it.id in selectionManager.selectedIds.value }
        DebugLogBuffer.log("GalleryViewModel", "startArchiving: targetUri=$targetUri, selectedIds=${selectionManager.selectedIds.value.size}, matchedSelected=${selected.size}")
        if (selected.isEmpty()) {
            DebugLogBuffer.log("GalleryViewModel", "startArchiving: selected list is empty, aborting.")
            return
        }

        if (vpsManager.isVpsEnabled()) {
            val limitGb = vpsManager.getVpsLimitGb()
            val limitBytes = limitGb.toLong() * 1024 * 1024 * 1024
            val archivedItemsSize = mediaItems.value.filter { it.status == MediaStatus.ARCHIVED_OTG }.sumOf { it.size }
            val newSelectionSize = selected.sumOf { it.size }
            if (archivedItemsSize + newSelectionSize > limitBytes) {
                Toast.makeText(getApplication(), getApplication<Application>().getString(by.w6.my1drive.R.string.vps_limit_exceeded, limitGb.toString()), Toast.LENGTH_LONG).show()
                return
            }
        }

        if (IS_LIMIT_ACTIVE) {
            val currentArchivedSize = physicalArchiveSize.value
            val newSelectionSize = selected.sumOf { it.size }
            val totalProjectedSize = currentArchivedSize + newSelectionSize
            if (totalProjectedSize > ARCHIVE_SIZE_LIMIT) {
                _showLimitReachedDialog.value = true
                DebugLogBuffer.log("GalleryViewModel", "startArchiving: limit reached, aborting.")
                return
            }
        }

        val context = getApplication<Application>()
        val uniqueFolders = selected.map { mediaOperationInteractor.getFolderToRequest(it.originalRelativePath) }
            .filter { it.isNotEmpty() }
            .toSet()

        val missingFolders = uniqueFolders.filter { !mediaOperationInteractor.hasPermissionForFolder(context, it) }

        if (missingFolders.isNotEmpty()) {
            mediaOperationInteractor.pendingArchiveTask = selected to targetUri
            mediaOperationInteractor.missingFoldersQueue.clear()
            mediaOperationInteractor.missingFoldersQueue.addAll(missingFolders)
            mediaOperationInteractor.requestNextFolderPermission()
            selectionManager.clearSelection()
        } else {
            syncHelper.startArchiving(selected, targetUri)
            selectionManager.clearSelection()
        }
    }

    fun archiveSingleItem(item: MediaItem, targetUri: Uri) {
        if (vpsManager.isVpsEnabled()) {
            val limitGb = vpsManager.getVpsLimitGb()
            val limitBytes = limitGb.toLong() * 1024 * 1024 * 1024
            val archivedItemsSize = mediaItems.value.filter { it.status == MediaStatus.ARCHIVED_OTG }.sumOf { it.size }
            if (archivedItemsSize + item.size > limitBytes) {
                Toast.makeText(getApplication(), getApplication<Application>().getString(by.w6.my1drive.R.string.vps_limit_exceeded, limitGb.toString()), Toast.LENGTH_LONG).show()
                return
            }
        }
        if (IS_LIMIT_ACTIVE) {
            val currentArchivedSize = physicalArchiveSize.value
            val totalProjectedSize = currentArchivedSize + item.size

            if (totalProjectedSize > ARCHIVE_SIZE_LIMIT) {
                _showLimitReachedDialog.value = true
                return
            }
        }

        val context = getApplication<Application>()
        val folder = mediaOperationInteractor.getFolderToRequest(item.originalRelativePath)
        if (folder.isNotEmpty() && !mediaOperationInteractor.hasPermissionForFolder(context, folder)) {
            mediaOperationInteractor.pendingArchiveTask = listOf(item) to targetUri
            mediaOperationInteractor.missingFoldersQueue.clear()
            mediaOperationInteractor.missingFoldersQueue.add(folder)
            mediaOperationInteractor.requestNextFolderPermission()
        } else {
            syncHelper.startArchiving(listOf(item), targetUri)
        }
    }
    fun restoreSingleItem(item: MediaItem) {
        archiveInteractor.startRestoring(listOf(item), null)
    }
    fun dismissError() { syncHelper.dismissError() }
    fun refresh() { repository.refresh() }

    val deviceDeleteSender: StateFlow<IntentSender?> = mediaOperationInteractor.deviceDeleteSender


    // ─── Delete state ───

    private val _pendingDelete = MutableStateFlow<List<MediaItem>?>(null)
    val pendingDelete: StateFlow<List<MediaItem>?> = _pendingDelete.asStateFlow()

    fun requestDeleteSelected() {
        val selected = mediaItems.value.filter { it.id in selectionManager.selectedIds.value }
        if (selected.isNotEmpty()) {
            _pendingDelete.value = selected
        }
    }

    fun confirmDelete() {
        val items = _pendingDelete.value ?: return
        _pendingDelete.value = null
        selectionManager.clearSelection()
        this@GalleryViewModel.mediaOperationInteractor.startDeletingWithPermissionCheck(items)
    }

    fun dismissDelete() { _pendingDelete.value = null }

    /** Called when user confirms device delete in system dialog */
    fun onDeviceDeleteConfirmed() = mediaOperationInteractor.onDeviceDeleteResult(true)

    /** Called when user cancels device delete in system dialog */
    fun onDeviceDeleteCancelled() = mediaOperationInteractor.onDeviceDeleteResult(false)

    private fun findFileInTree(context: Context, treeUri: Uri, relativePath: String?, displayName: String): DocumentFile? {
        val cleanPath = relativePath?.trim('/', '\\') ?: ""
        val fileName = displayName.trim()
        val fullRelativePath = if (cleanPath.isNotEmpty()) "$cleanPath/$fileName" else fileName

        // Method 1: Direct URI construction (Fast & O(1) for external storage provider)
        try {
            val authority = treeUri.authority
            if (authority == "com.android.externalstorage.documents") {
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val volumeId = treeDocId.substringBefore(":", "primary")
                val targetDocId = "$volumeId:$fullRelativePath"
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetDocId)
                val docFile = DocumentFile.fromSingleUri(context, fileUri)
                if (docFile != null && docFile.exists()) {
                    return docFile
                }
            }
        } catch (_: Exception) {}

        // Method 2: Traverse (Fallback for other providers)
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            if (!root.exists() || !root.canWrite()) return null

            val treeDocId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (_: Exception) {
                ""
            }
            val selectedPath = treeDocId.substringAfter(":", "").trim('/', '\\')
            val selectedSegments = selectedPath.split('/', '\\').filter { it.isNotEmpty() }
            val fileSegments = cleanPath.split('/', '\\').filter { it.isNotEmpty() }

            if (fileSegments.size >= selectedSegments.size) {
                var matches = true
                for (i in selectedSegments.indices) {
                    if (!fileSegments[i].equals(selectedSegments[i], ignoreCase = true)) {
                        matches = false
                        break
                    }
                }
                if (matches) {
                    val remainingSegments = fileSegments.drop(selectedSegments.size)
                    var currentDir: DocumentFile = root
                    var found = true
                    for (segment in remainingSegments) {
                        val nextDir = currentDir.findFile(segment)
                        if (nextDir != null && nextDir.isDirectory) {
                            currentDir = nextDir
                        } else {
                            found = false
                            break
                        }
                    }
                    if (found) {
                        val file = currentDir.findFile(fileName)
                        if (file != null && file.exists()) {
                            return file
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /** Unified device delete: works on all API levels, bypasses system prompt on API 30+ if deviceDirectoryUri is set */

    // ─── Restore ───

    fun setAskRestorePath(value: Boolean) { _askRestorePath.value = value; prefs.edit().putBoolean(PREF_ASK_RESTORE_PATH, value).apply() }

    fun requestRestore() {
        val selected = mediaItems.value.filter { it.id in selectionManager.selectedIds.value && it.status == MediaStatus.ARCHIVED_OTG }
        if (selected.isEmpty()) return
        archiveInteractor.startRestoring(selected, null)
        selectionManager.clearSelection()
    }

    fun restoreToOriginalPath() { pendingRestoreItems.toList().let { pendingRestoreItems = emptyList(); _restoreRequest.value = null; archiveInteractor.startRestoring(it, null); selectionManager.clearSelection() } }
    fun restoreToChosenFolder(uri: Uri) { pendingRestoreItems.toList().let { pendingRestoreItems = emptyList(); _restoreRequest.value = null; archiveInteractor.startRestoring(it, uri); selectionManager.clearSelection() } }
    fun dismissRestoreRequest() { pendingRestoreItems = emptyList(); _restoreRequest.value = null }
    
    fun resolveRestoreConflict(decision: by.w6.my1drive.ui.RestoreConflictDecision) {
        archiveInteractor.resolveRestoreConflict(decision)
    }



    fun dismissRestoreError() { restoreState.value = restoreState.value.copy(error = null) }
    fun dismissArchiveError() { syncHelper.dismissError() }


    // ─── Immediate Delete (fullscreen preview) ───

    fun deleteSingleItemImmediate(item: MediaItem) = mediaOperationInteractor.startDeletingWithPermissionCheck(listOf(item))

    // ─── Create folder ───

    fun requestCreateFolder() = mediaOperationInteractor.requestCreateFolder()
    fun dismissCreateFolderDialog() = mediaOperationInteractor.dismissCreateFolderDialog()
    fun createFolderOnOtg(folderName: String) = mediaOperationInteractor.createFolderOnOtg(folderName)

    // ─── Helpers ───


    val otgDirectoryDisplayName: StateFlow<String?> = otgManager.otgDirectoryUri.map { uri ->
        if (uri == null) return@map null
        val context = getApplication<Application>()
        try {
            val archiveDir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(context, uri, createIfNotExist = false)
            archiveDir?.name ?: DocumentFile.fromTreeUri(context, uri)?.name ?: Uri.decode(uri.toString().substringAfterLast("/"))
        } catch (e: Exception) {
            Uri.decode(uri.toString().substringAfterLast("/"))
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Lazily, null)

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

    fun shareMediaItem(item: MediaItem, context: Context, onError: (String) -> Unit) = mediaOperationInteractor.shareMediaItem(item, context, onError)

    fun shareSelectedItems(context: Context, onError: (String) -> Unit) {
        val selected = mediaItems.value.filter { it.id in selectionManager.selectedIds.value }
        if (selected.isNotEmpty()) {
            mediaOperationInteractor.shareSelectedItems(selected, context, onError)
            selectionManager.clearSelection()
        }
    }
}
