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

    private val _showFirstLaunchDialog = MutableStateFlow(false)
    val showFirstLaunchDialog = _showFirstLaunchDialog.asStateFlow()

    private val _showUnknownDriveDialog = MutableStateFlow(false)
    val showUnknownDriveDialog = _showUnknownDriveDialog.asStateFlow()

    private val _showUnreadableOtgDialog = MutableStateFlow(false)
    val showUnreadableOtgDialog = _showUnreadableOtgDialog.asStateFlow()

    private val _showWriteProtectedRootDialog = MutableStateFlow(false)
    val showWriteProtectedRootDialog = _showWriteProtectedRootDialog.asStateFlow()

    private val _showLocalFolderDialog = MutableStateFlow(false)
    val showLocalFolderDialog = _showLocalFolderDialog.asStateFlow()

    private val _showNamingDialog = MutableStateFlow<Uri?>(null)
    val showNamingDialog = _showNamingDialog.asStateFlow()

    private val _showCreateArchiveGuideDialog = MutableStateFlow<Uri?>(null)
    val showCreateArchiveGuideDialog = _showCreateArchiveGuideDialog.asStateFlow()

    fun showCreateArchiveGuideDialog(uri: Uri) { _showCreateArchiveGuideDialog.value = uri }
    fun dismissCreateArchiveGuideDialog() { _showCreateArchiveGuideDialog.value = null }

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
            onShowFirstLaunchDialog = { _showFirstLaunchDialog.value = it },
            onShowUnknownDriveDialog = { _showUnknownDriveDialog.value = it },
            onShowUnreadableOtgDialog = { _showUnreadableOtgDialog.value = it },
            onShowWriteProtectedRootDialog = { _showWriteProtectedRootDialog.value = it },
            onShowLocalFolderDialog = { _showLocalFolderDialog.value = it },
            onShowNamingDialog = { _showNamingDialog.value = it },
            onShowCreateArchiveGuideDialog = { _showCreateArchiveGuideDialog.value = it }
        )
    }

    fun dismissFirstLaunchDialog() { _showFirstLaunchDialog.value = false }
    fun dismissUnknownDriveDialog() { _showUnknownDriveDialog.value = false }
    fun dismissUnreadableOtgDialog() { _showUnreadableOtgDialog.value = false }
    fun dismissWriteProtectedRootDialog() { _showWriteProtectedRootDialog.value = false }
    fun dismissLocalFolderDialog() { _showLocalFolderDialog.value = false }
    fun dismissNamingDialog() { _showNamingDialog.value = null }
    fun triggerWriteProtectedRootDialog() { _showWriteProtectedRootDialog.value = true }
    fun showNamingDialog(uri: Uri) { _showNamingDialog.value = uri }

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
            selectedIds = _selectedIds
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
            onOperationComplete = {
                otgManager.updateArchiveSize()
                otgManager.setCheckingConnection(false)
            },
            onArchiveSuccess = { items ->
                deleteDeviceItems(items)
            },
            onItemArchived = { item ->
                _selectedIds.value = _selectedIds.value - item.id
            },
            onPreviewCached = { hash, path ->
                onPreviewCached(hash, path)
            }
        )
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

    private val _deviceSortMode = MutableStateFlow(
        try {
            DeviceSortMode.valueOf(
                prefs.getString("device_sort_mode", DeviceSortMode.BY_PHOTO_DATE.name) ?: DeviceSortMode.BY_PHOTO_DATE.name
            )
        } catch (e: Exception) {
            DeviceSortMode.BY_PHOTO_DATE
        }
    )
    val deviceSortMode = _deviceSortMode.asStateFlow()

    fun setDeviceSortMode(mode: DeviceSortMode) {
        _deviceSortMode.value = mode
        prefs.edit().putString("device_sort_mode", mode.name).apply()
    }

    private val _gridColumnsCount = MutableStateFlow(
        prefs.getInt("grid_columns_count", 3)
    )
    val gridColumnsCount = _gridColumnsCount.asStateFlow()

    fun setGridColumnsCount(count: Int) {
        _gridColumnsCount.value = count
        prefs.edit().putInt("grid_columns_count", count).apply()
    }

    val groupedMediaItems: StateFlow<List<GalleryItem>> = combine(
        mediaItems,
        deviceSortMode
    ) { list, sortMode ->
        val localItems = list.filter { it.status == MediaStatus.ON_DEVICE }.run {
            if (sortMode == DeviceSortMode.BY_RESTORE_DATE) {
                sortedByDescending { it.dateAdded ?: 0L }
            } else {
                sortedByDescending { it.dateModified }
            }
        }
        val resultList = mutableListOf<GalleryItem>()
        val grouped = localItems.groupBy { item ->
            val date = if (sortMode == DeviceSortMode.BY_RESTORE_DATE) {
                item.dateAdded ?: 0L
            } else {
                item.dateModified
            }
            formatDateHeader(date)
        }
        for ((headerText, items) in grouped) {
            resultList.add(GalleryItem.Header(headerText))
            items.forEach { resultList.add(GalleryItem.Media(it)) }
        }
        resultList
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _archiveSortMode = MutableStateFlow(
        try {
            ArchiveSortMode.valueOf(
                prefs.getString("archive_sort_mode", ArchiveSortMode.BY_PHOTO_DATE.name) ?: ArchiveSortMode.BY_PHOTO_DATE.name
            )
        } catch (e: Exception) {
            ArchiveSortMode.BY_PHOTO_DATE
        }
    )
    val archiveSortMode = _archiveSortMode.asStateFlow()

    fun setArchiveSortMode(mode: ArchiveSortMode) {
        _archiveSortMode.value = mode
        prefs.edit().putString("archive_sort_mode", mode.name).apply()
    }

    val archivedGroupedItems: StateFlow<List<GalleryItem>> = combine(
        mediaItems,
        archiveSortMode
    ) { list, sortMode ->
        val archivedList = list.filter {
            it.status == MediaStatus.ARCHIVED_OTG &&
            (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/"))
        }.run {
            if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) {
                sortedByDescending { it.dateArchived ?: 0L }
            } else {
                sortedByDescending { it.dateModified }
            }
        }

        val resultList = mutableListOf<GalleryItem>()
        val grouped = archivedList.groupBy { item ->
            val date = if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) {
                item.dateArchived ?: 0L
            } else {
                item.dateModified
            }
            formatDateHeader(date)
        }
        for ((headerText, items) in grouped) {
            resultList.add(GalleryItem.Header(headerText))
            items.forEach { resultList.add(GalleryItem.Media(it)) }
        }
        resultList
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

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

    private val _isSharingPreparing = MutableStateFlow(false)
    val isSharingPreparing = _isSharingPreparing.asStateFlow()

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog = _showCreateFolderDialog.asStateFlow()

    private var pendingArchiveTask: Pair<List<MediaItem>, Uri>? = null
    private var pendingDeleteTask: List<MediaItem>? = null
    private val missingFoldersQueue = mutableListOf<String>()

    private fun getFolderToRequest(relativePath: String?): String {
        if (relativePath.isNullOrEmpty()) return ""
        val clean = relativePath.trim('/', '\\').replace('\\', '/')
        val segments = clean.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return ""

        // If the path starts with Android, any subdirectory other than Android/media (like Android/data or Android/obb) is blocked.
        // If it starts with Android/media, we can request Android/media or a subfolder of it.
        if (segments.first().equals("Android", ignoreCase = true)) {
            if (segments.size >= 2 && segments[1].equals("media", ignoreCase = true)) {
                if (segments.size > 2) {
                    val parentSegments = segments.dropLast(1)
                    val parentPath = parentSegments.joinToString("/")
                    if (parentPath.equals("Android/media", ignoreCase = true)) {
                        return "Android/media"
                    }
                    return parentPath
                } else {
                    return "Android/media"
                }
            } else {
                return "Android/media"
            }
        }

        // If there is only 1 segment (e.g. "DCIM", "Pictures", "MyFolder"), parent would be root, which is blocked.
        // So request the folder itself.
        if (segments.size == 1) {
            return segments.first()
        }

        // If there are multiple segments, one level higher is dropLast(1)
        val parentSegments = segments.dropLast(1)
        val parentPath = parentSegments.joinToString("/")
        if (parentPath.isEmpty() || parentPath.equals("Android", ignoreCase = true)) {
            return segments.first()
        }
        return parentPath
    }

    private fun hasPermissionForFolder(context: Context, folderName: String): Boolean {
        if (folderName.isEmpty()) return false
        val persisted = context.contentResolver.persistedUriPermissions
        val reqSegments = folderName.split('/', '\\').filter { it.isNotEmpty() }
        return persisted.any { perm ->
            if (perm.uri.authority == "com.android.externalstorage.documents") {
                try {
                    val docId = DocumentsContract.getTreeDocumentId(perm.uri)
                    val volumeId = docId.substringBefore(":", "primary")
                    val path = docId.substringAfter(":", "").trim('/', '\\')
                    val permSegments = path.split('/', '\\').filter { it.isNotEmpty() }
                    if (volumeId.equals("primary", ignoreCase = true)) {
                        if (permSegments.isEmpty()) {
                            true
                        } else if (permSegments.size <= reqSegments.size) {
                            permSegments.indices.all { i ->
                                permSegments[i].equals(reqSegments[i], ignoreCase = true)
                            }
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
        }
    }

    private fun requestNextFolderPermission() {
        if (missingFoldersQueue.isNotEmpty()) {
            val nextFolder = missingFoldersQueue.first()
            showArchiveFolderAccessDialog(nextFolder)
        } else {
            val archiveTask = pendingArchiveTask
            if (archiveTask != null) {
                syncHelper.startArchiving(archiveTask.first, archiveTask.second)
                pendingArchiveTask = null
            }
            val deleteTask = pendingDeleteTask
            if (deleteTask != null) {
                val itemsToDelete = deleteTask
                pendingDeleteTask = null
                deleteDeviceItems(itemsToDelete.filter { it.status == MediaStatus.ON_DEVICE })
                archiveInteractor.deleteArchivedItems(itemsToDelete.filter { it.status == MediaStatus.ARCHIVED_OTG })
            }
        }
    }

    fun onFolderPermissionGranted(uri: Uri) {
        if (missingFoldersQueue.isNotEmpty()) {
            val folder = missingFoldersQueue.removeAt(0)
            DebugLogBuffer.log("GalleryViewModel", "Permission granted for folder: $folder. Remaining: ${missingFoldersQueue.size}")
            requestNextFolderPermission()
        }
    }

    fun onFolderPermissionCancelled() {
        missingFoldersQueue.clear()
        pendingArchiveTask = null
        pendingDeleteTask = null
        _showArchiveFolderAccessDialog.value = false
        _archiveAccessFolderPath.value = null
        DebugLogBuffer.log("GalleryViewModel", "Folder permission request cancelled. Aborting task.")
    }

    private fun findMatchingTreeUriForFile(context: Context, relativePath: String?): Uri? {
        val folderName = getFolderToRequest(relativePath)
        if (folderName.isEmpty()) return null
        val persisted = context.contentResolver.persistedUriPermissions
        val reqSegments = folderName.split('/', '\\').filter { it.isNotEmpty() }
        val matchedPerm = persisted.firstOrNull { perm ->
            if (perm.uri.authority == "com.android.externalstorage.documents") {
                try {
                    val docId = DocumentsContract.getTreeDocumentId(perm.uri)
                    val volumeId = docId.substringBefore(":", "primary")
                    val path = docId.substringAfter(":", "").trim('/', '\\')
                    val permSegments = path.split('/', '\\').filter { it.isNotEmpty() }
                    if (volumeId.equals("primary", ignoreCase = true)) {
                        if (permSegments.isEmpty()) {
                            true
                        } else if (permSegments.size <= reqSegments.size) {
                            permSegments.indices.all { i ->
                                permSegments[i].equals(reqSegments[i], ignoreCase = true)
                            }
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
        }
        return matchedPerm?.uri
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
            otgManager.activeArchiveUuid.collect { _ ->
                updateMissingThumbnailsCount()
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
            } catch (_: Exception) {}
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

    fun refreshCacheStats() { viewModelScope.launch(Dispatchers.IO) { _cacheStats.value = Pair(previewCache.getCacheSize(), previewCache.getCacheFileCount()) } }
    fun clearPreviewCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                previewCache.clearAll()
                db.mediaDao().deleteAll()
                getApplication<Application>().getSharedPreferences("my1drive_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("preview_cache_unlimited", false).apply()
            }
            otgManager.resetActiveArchiveUuid()
            refreshCacheStats()
            repository.refresh()
        }
    }

    private val _isSyncingThumbnails = MutableStateFlow(false)
    val isSyncingThumbnails = _isSyncingThumbnails.asStateFlow()

    private val _syncThumbnailsProgress = MutableStateFlow(Pair(0, 0))
    val syncThumbnailsProgress = _syncThumbnailsProgress.asStateFlow()

    private var thumbnailSyncJob: kotlinx.coroutines.Job? = null

    fun getMissingThumbnailsCount(): Int {
        return 0 // computed via state update
    }

    private val _missingThumbnailsCount = MutableStateFlow(0)
    val missingThumbnailsCount = _missingThumbnailsCount.asStateFlow()

    fun updateMissingThumbnailsCount() {
        val activeUuid = otgManager.activeArchiveUuid.value
        if (activeUuid == null) {
            _missingThumbnailsCount.value = 0
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val count = db.mediaDao().getWithoutPreviewCount(activeUuid)
            _missingThumbnailsCount.value = count
        }
    }

    fun startThumbnailSync() {
        val activeUuid = otgManager.activeArchiveUuid.value ?: return
        _isSyncingThumbnails.value = true
        _syncThumbnailsProgress.value = Pair(0, 0)
        thumbnailSyncJob = viewModelScope.launch {
            val job = coroutineContext[kotlinx.coroutines.Job]
            try {
                syncHelper.syncAllThumbnails(
                    activeUuid = activeUuid,
                    isCancelled = { job?.isActive == false },
                    onProgress = { current, total ->
                        _syncThumbnailsProgress.value = Pair(current, total)
                    }
                )
            } finally {
                _isSyncingThumbnails.value = false
                updateMissingThumbnailsCount()
                refreshCacheStats()
            }
        }
    }

    fun cancelThumbnailSync() {
        thumbnailSyncJob?.cancel()
        _isSyncingThumbnails.value = false
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
        if (!hasPermissionForFolder(context, "DCIM")) {
            _pendingDeviceFolderToRequest.value = "DCIM"
            otgManager.showLocalFolderPrompt()
        }
    }

    fun ejectOtg() {
        otgManager.onEject()
    }

    fun cancelArchiving() {
        syncHelper.cancelArchiving()
    }

    fun cancelRestoring() {
        archiveInteractor.isRestoreCancellationRequested = true
    }

    // ─── Selection ───

    fun toggleSelection(itemId: String) { _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (contains(itemId)) remove(itemId) else add(itemId) } }
    fun clearSelection() { _selectedIds.value = emptySet() }
    fun selectItems(itemIds: Collection<String>) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { addAll(itemIds) }
    }
    fun deselectItems(itemIds: Collection<String>) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { removeAll(itemIds.toSet()) }
    }

    // ─── Sync & Archive delegated ───

    fun isVpsEnabled(): Boolean = vpsManager.isVpsEnabled()
    fun silentSyncArchive() { syncHelper.silentSyncArchive(otgManager.otgDirectoryUri.value) }
    fun dismissMissingFilesNotification() { syncHelper.dismissMissingFilesNotification() }
    fun dismissAutoSyncAddedCount() { syncHelper.dismissAutoSyncAddedCount() }
    fun syncArchive() { syncHelper.syncArchive(otgManager.otgDirectoryUri.value) }
    fun dismissSync() { syncHelper.dismissSync() }
    fun startArchiving(targetUri: Uri) {
        val selected = mediaItems.value.filter { it.id in _selectedIds.value }
        DebugLogBuffer.log("GalleryViewModel", "startArchiving: targetUri=$targetUri, selectedIds=${_selectedIds.value.size}, matchedSelected=${selected.size}")
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
                Toast.makeText(getApplication(), "Превышен настроенный лимит VPS (${limitGb} ГБ). Архивирование отменено.", Toast.LENGTH_LONG).show()
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
        val uniqueFolders = selected.map { getFolderToRequest(it.originalRelativePath) }
            .filter { it.isNotEmpty() }
            .toSet()

        val missingFolders = uniqueFolders.filter { !hasPermissionForFolder(context, it) }

        if (missingFolders.isNotEmpty()) {
            pendingArchiveTask = selected to targetUri
            missingFoldersQueue.clear()
            missingFoldersQueue.addAll(missingFolders)
            requestNextFolderPermission()
            _selectedIds.value = emptySet()
        } else {
            syncHelper.startArchiving(selected, targetUri)
            _selectedIds.value = emptySet()
        }
    }

    fun archiveSingleItem(item: MediaItem, targetUri: Uri) {
        if (vpsManager.isVpsEnabled()) {
            val limitGb = vpsManager.getVpsLimitGb()
            val limitBytes = limitGb.toLong() * 1024 * 1024 * 1024
            val archivedItemsSize = mediaItems.value.filter { it.status == MediaStatus.ARCHIVED_OTG }.sumOf { it.size }
            if (archivedItemsSize + item.size > limitBytes) {
                Toast.makeText(getApplication(), "Превышен настроенный лимит VPS (${limitGb} ГБ). Архивирование отменено.", Toast.LENGTH_LONG).show()
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
        val folder = getFolderToRequest(item.originalRelativePath)
        if (folder.isNotEmpty() && !hasPermissionForFolder(context, folder)) {
            pendingArchiveTask = listOf(item) to targetUri
            missingFoldersQueue.clear()
            missingFoldersQueue.add(folder)
            requestNextFolderPermission()
        } else {
            syncHelper.startArchiving(listOf(item), targetUri)
        }
    }
    fun restoreSingleItem(item: MediaItem) {
        archiveInteractor.startRestoring(listOf(item), null)
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
        startDeletingWithPermissionCheck(items)
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
    private fun deleteDeviceItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val context = getApplication<Application>()
        val remainingItems = mutableListOf<MediaItem>()
        var anyDeleted = false
        for (item in items) {
            val treeUri = findMatchingTreeUriForFile(context, item.originalRelativePath)
            val doc = if (treeUri != null) {
                findFileInTree(context, treeUri, item.originalRelativePath, item.displayName)
            } else null
            if (doc != null && doc.exists() && doc.delete()) {
                anyDeleted = true
                // Scan the file path so MediaStore removes it
                val externalDir = android.os.Environment.getExternalStorageDirectory()
                val relPath = item.originalRelativePath?.trim('/', '\\') ?: ""
                val fileOnDisk = if (relPath.isNotEmpty()) {
                    java.io.File(externalDir, "$relPath/${item.displayName}")
                } else {
                    java.io.File(externalDir, item.displayName)
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(fileOnDisk.absolutePath),
                    arrayOf(item.mimeType)
                ) { _, _ ->
                    viewModelScope.launch {
                        repository.refresh()
                    }
                }
            } else {
                remainingItems.add(item)
            }
        }
        if (anyDeleted && remainingItems.isEmpty()) {
            // All items were deleted via SAF
            return
        }
        if (remainingItems.isNotEmpty()) {
            fallbackDeleteDeviceItems(remainingItems)
        }
    }

    private fun fallbackDeleteDeviceItems(items: List<MediaItem>) {
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
        // Removed local folder prompt from deletion to ask in setup phase instead
        _deviceDeletePendingItems.clear()
        viewModelScope.launch { repository.refresh() }
    }

    // ─── Restore ───

    fun setAskRestorePath(value: Boolean) { _askRestorePath.value = value; prefs.edit().putBoolean(PREF_ASK_RESTORE_PATH, value).apply() }

    fun requestRestore() {
        val selected = mediaItems.value.filter { it.id in _selectedIds.value && it.status == MediaStatus.ARCHIVED_OTG }
        if (selected.isEmpty()) return
        archiveInteractor.startRestoring(selected, null)
        _selectedIds.value = emptySet()
    }

    fun restoreToOriginalPath() { pendingRestoreItems.toList().let { pendingRestoreItems = emptyList(); _restoreRequest.value = null; archiveInteractor.startRestoring(it, null); _selectedIds.value = emptySet() } }
    fun restoreToChosenFolder(uri: Uri) { pendingRestoreItems.toList().let { pendingRestoreItems = emptyList(); _restoreRequest.value = null; archiveInteractor.startRestoring(it, uri); _selectedIds.value = emptySet() } }
    fun dismissRestoreRequest() { pendingRestoreItems = emptyList(); _restoreRequest.value = null }
    
    fun resolveRestoreConflict(decision: by.w6.my1drive.ui.RestoreConflictDecision) {
        archiveInteractor.resolveRestoreConflict(decision)
    }



    fun dismissRestoreError() { restoreState.value = restoreState.value.copy(error = null) }
    fun dismissArchiveError() { syncHelper.dismissError() }


    // ─── Immediate Delete (fullscreen preview) ───

    fun deleteSingleItemImmediate(item: MediaItem) {
        startDeletingWithPermissionCheck(listOf(item))
    }

    private fun startDeletingWithPermissionCheck(items: List<MediaItem>) {
        val context = getApplication<Application>()
        val deviceItems = items.filter { it.status == MediaStatus.ON_DEVICE }
        val archivedItems = items.filter { it.status == MediaStatus.ARCHIVED_OTG }
        
        val uniqueFolders = deviceItems.map { getFolderToRequest(it.originalRelativePath) }
            .filter { it.isNotEmpty() }
            .toSet()
            
        val missingFolders = uniqueFolders.filter { !hasPermissionForFolder(context, it) }

        if (missingFolders.isNotEmpty()) {
            pendingDeleteTask = items
            missingFoldersQueue.clear()
            missingFoldersQueue.addAll(missingFolders)
            requestNextFolderPermission()
        } else {
            deleteDeviceItems(deviceItems)
            archiveInteractor.deleteArchivedItems(archivedItems)
        }
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

    fun shareMediaItem(item: MediaItem, context: Context, onError: (String) -> Unit) {
        if (item.status == MediaStatus.ON_DEVICE) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, item.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Поделиться"))
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Ошибка при отправке файла")
            }
        } else if (item.status == MediaStatus.ARCHIVED_OTG) {
            val activeUuid = otgManager.activeArchiveUuid.value
            val isCurrentConnected = isOtgConnected.value && item.archiveUuid == activeUuid
            
            if (isCurrentConnected && !item.otgUri.isNullOrEmpty()) {
                _isSharingPreparing.value = true
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val otgUri = Uri.parse(item.otgUri)
                        val inputStream = context.contentResolver.openInputStream(otgUri)
                            ?: throw Exception("Не удалось открыть файл на накопителе")

                        val sharedTempDir = File(context.cacheDir, "shared_temp").also { it.mkdirs() }
                        val tempFile = File(sharedTempDir, item.displayName)
                        
                        inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val fileUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            tempFile
                        )

                        _isSharingPreparing.value = false

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = item.mimeType
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Поделиться"))
                    } catch (e: Exception) {
                        _isSharingPreparing.value = false
                        viewModelScope.launch(Dispatchers.Main) {
                            onError(e.localizedMessage ?: "Ошибка при подготовке файла из архива")
                        }
                    }
                }
            } else {
                val thumbPath = item.thumbnailPath
                if (thumbPath.isNullOrEmpty()) {
                    onError("Накопитель отключен и локальный эскиз отсутствует")
                    return
                }
                _isSharingPreparing.value = true
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val thumbFile = File(thumbPath)
                        if (!thumbFile.exists()) {
                            throw Exception("Файл эскиза не найден на устройстве")
                        }
                        
                        val sharedTempDir = File(context.cacheDir, "shared_temp").also { it.mkdirs() }
                        val tempFile = File(sharedTempDir, item.displayName)
                        thumbFile.inputStream().use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        val fileUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            tempFile
                        )
                        _isSharingPreparing.value = false
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = item.mimeType
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Поделиться эскизом"))
                    } catch (e: Exception) {
                        _isSharingPreparing.value = false
                        viewModelScope.launch(Dispatchers.Main) {
                            onError(e.localizedMessage ?: "Ошибка при подготовке эскиза")
                        }
                    }
                }
            }
        }
    }
    fun shareSelectedItems(context: Context, onError: (String) -> Unit) {
        val selected = mediaItems.value.filter { it.id in _selectedIds.value }
        if (selected.isEmpty()) return

        val onDeviceItems = selected.filter { it.status == MediaStatus.ON_DEVICE }
        val archivedItems  = selected.filter { it.status == MediaStatus.ARCHIVED_OTG }

        if (archivedItems.isEmpty()) {
            // All on-device — share directly without copying
            val uris = ArrayList(onDeviceItems.map { it.uri })
            val mimeType = commonMimeType(onDeviceItems)
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = onDeviceItems.first().mimeType
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            try {
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.preview_share)))
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Ошибка при отправке файлов")
            }
        } else {
            // Some files are in OTG archive — need to copy to cache first
            _isSharingPreparing.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val sharedTempDir = File(context.cacheDir, "shared_temp").also { it.mkdirs() }
                    val uris = ArrayList<Uri>()
                    val activeUuid = otgManager.activeArchiveUuid.value
                    val isOtgConnectedVal = isOtgConnected.value

                    // Add on-device items directly (no copy needed)
                    onDeviceItems.forEach { uris.add(it.uri) }

                    // Copy archived items (original if connected, thumbnail if offline)
                    for (item in archivedItems) {
                        val isCurrentConnected = isOtgConnectedVal && item.archiveUuid == activeUuid
                        var copied = false
                        
                        if (isCurrentConnected && !item.otgUri.isNullOrEmpty()) {
                            try {
                                val otgUri = Uri.parse(item.otgUri)
                                val inputStream = context.contentResolver.openInputStream(otgUri)
                                if (inputStream != null) {
                                    val tempFile = File(sharedTempDir, item.displayName)
                                    inputStream.use { input ->
                                        tempFile.outputStream().use { output -> input.copyTo(output) }
                                    }
                                    uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile))
                                    copied = true
                                }
                            } catch (_: Exception) {}
                        }
                        
                        if (!copied) {
                            // Try copying local thumbnail as fallback
                            val thumbPath = item.thumbnailPath
                            if (!thumbPath.isNullOrEmpty()) {
                                val thumbFile = File(thumbPath)
                                if (thumbFile.exists()) {
                                    val tempFile = File(sharedTempDir, item.displayName)
                                    try {
                                        thumbFile.inputStream().use { input ->
                                            tempFile.outputStream().use { output -> input.copyTo(output) }
                                        }
                                        uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile))
                                        copied = true
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        
                        if (!copied) {
                            throw Exception("Не удалось прочитать файл «${item.displayName}» (накопитель отключен и нет локального эскиза)")
                        }
                    }

                    _isSharingPreparing.value = false

                    val mimeType = commonMimeType(selected)
                    val intent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = selected.first().mimeType
                            putExtra(Intent.EXTRA_STREAM, uris.first())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = mimeType
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.preview_share)))
                } catch (e: Exception) {
                    _isSharingPreparing.value = false
                    viewModelScope.launch(Dispatchers.Main) {
                        onError(e.localizedMessage ?: "Ошибка при подготовке файлов для отправки")
                    }
                }
            }
        }
    }

    private fun commonMimeType(items: List<MediaItem>): String {
        return when {
            items.all { it.mimeType.startsWith("image/") } -> "image/*"
            items.all { it.mimeType.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
    }
}
