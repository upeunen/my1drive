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
            onOperationComplete = {
                otgManager.updateArchiveSize()
                otgManager.setCheckingConnection(false)
            },
            onArchiveSuccess = { items ->
                deleteDeviceItems(items)
                syncHelper.incrementActionCount()
            }
        )
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
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    private var pendingArchiveTask: Pair<List<MediaItem>, Uri>? = null
    private val missingFoldersQueue = mutableListOf<String>()

    private fun getLevel1Folder(relativePath: String?): String {
        if (relativePath.isNullOrEmpty()) return ""
        val clean = relativePath.trim('/', '\\').replace('\\', '/')
        return clean.substringBefore('/')
    }

    private fun hasPermissionForFolder(context: Context, folderName: String): Boolean {
        if (folderName.isEmpty()) return false
        val persisted = context.contentResolver.persistedUriPermissions
        return persisted.any { perm ->
            if (perm.uri.authority == "com.android.externalstorage.documents") {
                try {
                    val docId = DocumentsContract.getTreeDocumentId(perm.uri)
                    val volumeId = docId.substringBefore(":", "primary")
                    val path = docId.substringAfter(":", "").trim('/', '\\')
                    val segments = path.split('/', '\\').filter { it.isNotEmpty() }
                    if (volumeId.equals("primary", ignoreCase = true)) {
                        if (segments.isEmpty()) {
                            true
                        } else {
                            segments.first().equals(folderName, ignoreCase = true)
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
            val task = pendingArchiveTask
            if (task != null) {
                // Clear selection for these items immediately as archiving starts
                _selectedIds.value = _selectedIds.value - task.first.map { it.id }.toSet()
                syncHelper.startArchiving(task.first, task.second)
                pendingArchiveTask = null
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
        _showArchiveFolderAccessDialog.value = false
        _archiveAccessFolderPath.value = null
        DebugLogBuffer.log("GalleryViewModel", "Folder permission request cancelled. Aborting archiving task.")
    }

    private fun findMatchingTreeUriForFile(context: Context, relativePath: String?): Uri? {
        val folderName = getLevel1Folder(relativePath)
        if (folderName.isEmpty()) return null
        val persisted = context.contentResolver.persistedUriPermissions
        val matchedPerm = persisted.firstOrNull { perm ->
            if (perm.uri.authority == "com.android.externalstorage.documents") {
                try {
                    val docId = DocumentsContract.getTreeDocumentId(perm.uri)
                    val volumeId = docId.substringBefore(":", "primary")
                    val path = docId.substringAfter(":", "").trim('/', '\\')
                    val segments = path.split('/', '\\').filter { it.isNotEmpty() }
                    if (volumeId.equals("primary", ignoreCase = true)) {
                        segments.isEmpty() || segments.first().equals(folderName, ignoreCase = true)
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

        // Start the polling loop
        otgManager.start(savedUri, savedDeviceUri)
    }

    fun updateOtgStatus(isStartup: Boolean = false) {
        otgManager.onPhysicalConnectionChanged(isStartup)
    }

    fun dismissFirstLaunchDialog() {
        otgManager.dismissFirstLaunchDialog()
    }

    fun setDeviceDirectory(uri: Uri) {
        otgManager.onDeviceUriSelected(uri)
        onFolderPermissionGranted(uri)
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
        if (otgManager.deviceDirectoryUri.value == null) {
            _pendingDeviceFolderToRequest.value = "DCIM"
            otgManager.showLocalFolderPrompt()
        }
    }

    fun ejectOtg() {
        otgManager.onEject()
    }

    // ─── Selection ───

    fun toggleSelection(itemId: String) { _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (contains(itemId)) remove(itemId) else add(itemId) } }
    fun clearSelection() { _selectedIds.value = emptySet() }
    fun selectItems(itemIds: Collection<String>) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply { addAll(itemIds) }
    }

    // ─── Sync & Archive delegated ───

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
        val uniqueFolders = selected.map { getLevel1Folder(it.originalRelativePath) }
            .filter { it.isNotEmpty() }
            .toSet()

        val missingFolders = uniqueFolders.filter { !hasPermissionForFolder(context, it) }

        if (missingFolders.isNotEmpty()) {
            pendingArchiveTask = selected to targetUri
            missingFoldersQueue.clear()
            missingFoldersQueue.addAll(missingFolders)
            requestNextFolderPermission()
        } else {
            // Clear selection for these items immediately
            _selectedIds.value = _selectedIds.value - selected.map { it.id }.toSet()
            syncHelper.startArchiving(selected, targetUri)
        }
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

        val context = getApplication<Application>()
        val folder = getLevel1Folder(item.originalRelativePath)
        if (folder.isNotEmpty() && !hasPermissionForFolder(context, folder)) {
            pendingArchiveTask = listOf(item) to targetUri
            missingFoldersQueue.clear()
            missingFoldersQueue.add(folder)
            requestNextFolderPermission()
        } else {
            // Clear selection for this item immediately
            _selectedIds.value = _selectedIds.value - item.id
            syncHelper.startArchiving(listOf(item), targetUri)
        }
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
        syncHelper.incrementActionCount()
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
        // Clear selection for these items immediately as restoration starts
        _selectedIds.value = _selectedIds.value - items.map { it.id }.toSet()
        _restoringItemIds.value = items.map { it.id }.toSet()
        viewModelScope.launch {
            val logTag = "RestoreManager"
            try {
                DebugLogBuffer.log(logTag, "Start startRestoring for ${items.size} items, targetDirUri=$targetDirUri")
                var successCount = 0; val errors = mutableListOf<String>()
                val otgUri = otgManager.otgDirectoryUri.value
                restoreState.value = RestoreState(isRestoring = true, totalFiles = items.size)
                for ((index, item) in items.withIndex()) {
                    DebugLogBuffer.log(logTag, "Restoring item [${index + 1}/${items.size}]: ${item.displayName}")
                    restoreState.value = restoreState.value.copy(currentFileName = item.displayName, currentFileIndex = index + 1, currentStep = "")
                    archiveUtil.restoreItem(item, targetDirUri).collect { result ->
                        when (result) {
                            is by.w6.my1drive.utils.RestoreResult.Progress -> restoreState.value = restoreState.value.copy(
                                currentStep = result.step, progressFraction = (index.toFloat() + result.progressFraction) / items.size
                            )
                            is by.w6.my1drive.utils.RestoreResult.Success -> {
                                successCount++
                                DebugLogBuffer.log(logTag, "Item restored successfully: ${result.item.displayName}. Starting cleanup on OTG...")
                                try {
                                    // 1. Remove from JSON metadata on OTG drive (source of truth)
                                    if (otgUri != null && result.item.hash != null) {
                                        metadataStore.removeEntry(otgUri, result.item.hash)
                                        DebugLogBuffer.log(logTag, "Removed metadata entry from JSON for ${result.item.displayName}")
                                    }
                                    // 2. Delete physical file from OTG drive
                                    result.item.otgUri?.let { fileUri ->
                                        try {
                                            val otgFile = DocumentFile.fromSingleUri(getApplication(), Uri.parse(fileUri))
                                            val deleted = otgFile?.delete() ?: false
                                            DebugLogBuffer.log(logTag, "Deleted physical file from OTG: ${result.item.displayName}, success=$deleted")
                                        } catch (ex: Exception) {
                                            DebugLogBuffer.log(logTag, "Failed to delete physical file on OTG for ${result.item.displayName}: ${ex.localizedMessage}")
                                        }
                                    }
                                    // 3. Remove from Room (local cache)
                                    repository.deleteArchivedItem(result.item)
                                    DebugLogBuffer.log(logTag, "Deleted item from local Room DB: ${result.item.displayName}")
                                } catch (e: Exception) {
                                    DebugLogBuffer.log(logTag, "Error in OTG cleanup after restore for ${result.item.displayName}: ${e.localizedMessage}")
                                }
                            }
                            is by.w6.my1drive.utils.RestoreResult.Error -> {
                                val errStr = "${result.displayName}: ${result.message}"
                                errors.add(errStr)
                                DebugLogBuffer.log(logTag, "Item restoration failed: $errStr")
                            }
                        }
                    }
                    _restoringItemIds.value = _restoringItemIds.value - item.id
                }
                repository.refresh()
                val finalError = when {
                    errors.isNotEmpty() -> "Восстановлено: $successCount из ${items.size}.\n\nОшибки:\n" + errors.joinToString("\n")
                    successCount < items.size -> "Восстановлено: $successCount из ${items.size}."
                    else -> null
                }
                DebugLogBuffer.log(logTag, "Restoration complete. Succeeded: $successCount, Failed: ${errors.size}. Final error: $finalError")
                restoreState.value = RestoreState(isRestoring = false, successCount = successCount, error = finalError)
                otgManager.updateArchiveSize()
                syncHelper.incrementActionCount()
            } finally {
                _restoringItemIds.value = emptySet()
            }
        }
    }

    fun dismissRestoreError() { restoreState.value = restoreState.value.copy(error = null) }
    fun dismissArchiveError() { syncHelper.dismissError() }


    // ─── Immediate Delete (fullscreen preview) ───

    fun deleteSingleItemImmediate(item: MediaItem) {
        if (item.status == MediaStatus.ON_DEVICE) {
            deleteDeviceItems(listOf(item))
        } else {
            deleteArchivedItems(listOf(item))
        }
        syncHelper.incrementActionCount()
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
