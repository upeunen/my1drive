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

import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.profile.Attribute
import io.appmetrica.analytics.profile.UserProfile
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
import by.w6.my1drive.ui.model.YearGroup
import java.io.File

private const val PREFS_NAME = "my1drive_prefs"
private const val PREF_ASK_RESTORE_PATH = "ask_restore_path"
private const val PREF_HAS_SEEN_USB_TOOLTIP = "has_seen_usb_tooltip"
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

    private val _activeDialog = kotlinx.coroutines.flow.MutableStateFlow<AppDialog?>(
        if (!prefs.getBoolean(PREF_HAS_SEEN_USB_TOOLTIP, false)) AppDialog.UsbTooltip else null
    )
    val activeDialog = _activeDialog.asStateFlow()

    val limitRepository = by.w6.my1drive.data.local.LimitRepository(application)
    val photosArchivedCount = limitRepository.photosArchivedCountFlow
    val videosArchivedCount = limitRepository.videosArchivedCountFlow
    val isPremiumUnlocked = limitRepository.isPremiumUnlockedFlow
    private val remoteConfigManager = by.w6.my1drive.billing.RuStoreRemoteConfigManager.getInstance()

    fun showCreateArchiveGuideDialog(uri: Uri) { _activeDialog.value = AppDialog.CreateArchiveGuide(uri) }
    
    fun showPaywall(missingPhotos: Int = 0, missingVideos: Int = 0) { _activeDialog.value = AppDialog.Paywall(missingPhotos, missingVideos) }

    fun showPromoCodeDialog() { _activeDialog.value = AppDialog.PromoCode }

    /**
     * Проверяет промокод и активирует его при совпадении.
     * @return кол-во дней если код верный, null если неверный
     */
    fun applyPromoCode(code: String): Int? {
        val json = remoteConfigManager.promoCodesJson.value
        val days = by.w6.my1drive.billing.PromoCodeValidator.validate(code, json)
        return if (days != null) {
            val until = System.currentTimeMillis() + days.toLong() * 24 * 60 * 60 * 1000
            limitRepository.appliedPromoCode = code.trim().uppercase()
            limitRepository.promoUntilTimestamp = until
            dismissDialog()
            io.appmetrica.analytics.AppMetrica.reportEvent(
                "promo_code_applied",
                "{\"code\":\"${code.trim().uppercase()}\",\"days\":$days}"
            )
            days
        } else {
            io.appmetrica.analytics.AppMetrica.reportEvent(
                "promo_code_invalid",
                "{\"code\":\"${code.trim().uppercase()}\"}"
            )
            null
        }
    }
    
    val billingManager = by.w6.my1drive.billing.RuStoreBillingManager(application)

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
            onShowFirstLaunchDialog = { v -> if (v) _activeDialog.value = AppDialog.FirstLaunch else if (_activeDialog.value is AppDialog.FirstLaunch) _activeDialog.value = null },
            onShowUnknownDriveDialog = { v -> if (v) _activeDialog.value = AppDialog.UnknownDrive else if (_activeDialog.value is AppDialog.UnknownDrive) _activeDialog.value = null },
            onShowUnreadableOtgDialog = { v -> if (v) _activeDialog.value = AppDialog.UnreadableOtg else if (_activeDialog.value is AppDialog.UnreadableOtg) _activeDialog.value = null },
            onShowWriteProtectedRootDialog = { v -> if (v) _activeDialog.value = AppDialog.WriteProtectedRoot else if (_activeDialog.value is AppDialog.WriteProtectedRoot) _activeDialog.value = null },
            onShowLocalFolderDialog = { v -> if (v) _activeDialog.value = AppDialog.LocalFolder else if (_activeDialog.value is AppDialog.LocalFolder) _activeDialog.value = null },
            onShowNamingDialog = { v -> if (v != null) _activeDialog.value = AppDialog.Naming(v) else if (_activeDialog.value is AppDialog.Naming) _activeDialog.value = null },
            onShowCreateArchiveGuideDialog = { v -> if (v != null) _activeDialog.value = AppDialog.CreateArchiveGuide(v) else if (_activeDialog.value is AppDialog.CreateArchiveGuide) _activeDialog.value = null }
        )
    }

    fun dismissDialog() { 
        _activeDialog.value = null
        pendingItemsToDelete?.let { items ->
            mediaOperationInteractor.startDeletingWithPermissionCheck(items)
            pendingItemsToDelete = null
        }
    }
    fun markUsbTooltipSeen() { 
        prefs.edit().putBoolean(PREF_HAS_SEEN_USB_TOOLTIP, true).apply()
        if (_activeDialog.value is AppDialog.UsbTooltip) _activeDialog.value = null 
    }

    fun triggerWriteProtectedRootDialog() { _activeDialog.value = AppDialog.WriteProtectedRoot }
    fun showNamingDialog(uri: Uri) { _activeDialog.value = AppDialog.Naming(uri) }

    fun hasAllFilesAccess(): Boolean {
        return mediaOperationInteractor.hasAllFilesAccess(getApplication())
    }

    fun proceedWithManageStorageRequest(items: List<MediaItem>?) {
        _activeDialog.value = null
        mediaOperationInteractor.dispatchManageStorageIntent(items)
    }

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


    // uiState is initialized at the bottom of the class

    


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
    
    val archiveYearGroups = displayManager.archiveYearGroups
    
    val archiveFilterUuid = displayManager.archiveFilterUuid
    fun setArchiveFilterUuid(uuid: String?) { displayManager.setArchiveFilterUuid(uuid) }

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
            },
            onShowManageStorageDialog = { items ->
                _activeDialog.value = AppDialog.ManageStoragePermission(items)
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
        billingManager.checkPurchases { isPremium ->
            if (isPremium) {
                limitRepository.isPremiumUnlocked = true
            }
        }
        
        viewModelScope.launch {
            limitRepository.isPremiumUnlockedFlow.collect { isPremium ->
                if (isPremium && _activeDialog.value is AppDialog.Paywall) {
                    dismissDialog()
                }
            }
        }
        
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

    private var lastStorageCheckTime = 0L
    private var lastStorageCheckResult = false

    private fun checkIsStorageLow(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastStorageCheckTime < 60_000L) {
            return lastStorageCheckResult
        }
        return try {
            val context = getApplication<Application>()
            val stat = android.os.StatFs(context.filesDir.absolutePath)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            lastStorageCheckResult = bytesAvailable < 500L * 1024 * 1024
            lastStorageCheckTime = now
            lastStorageCheckResult
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
            val itemsToDelete = db.mediaDao().getByArchiveUuidSync(uuid)
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
        var selected = mediaItems.value.filter { it.id in selectionManager.selectedIds.value }
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

        val trialDays = remoteConfigManager.freeTrialDays.value
        if (limitRepository.shouldApplyLimits(trialDays, getApplication())) {
            val photos = selected.filter { !it.mimeType.startsWith("video/") }
            val videos = selected.filter { it.mimeType.startsWith("video/") }

            val maxPhotos = remoteConfigManager.maxPhotos.value
            val maxVideos = remoteConfigManager.maxVideos.value
            val allowedPhotosCount = (maxPhotos - limitRepository.photosArchivedCount).coerceAtLeast(0)
            val allowedVideosCount = (maxVideos - limitRepository.videosArchivedCount).coerceAtLeast(0)

            val allowedPhotos = photos.take(allowedPhotosCount)
            val allowedVideos = videos.take(allowedVideosCount)

            val pendingPhotos = photos.size - allowedPhotos.size
            val pendingVideos = videos.size - allowedVideos.size

            if (pendingPhotos > 0 || pendingVideos > 0) {
                AppMetrica.reportEvent("soft_cap_triggered")
                pendingForPaywallPhotos = pendingPhotos
                pendingForPaywallVideos = pendingVideos

                val allowedItems = allowedPhotos + allowedVideos
                if (allowedItems.isEmpty()) {
                    showPaywall(pendingPhotos, pendingVideos)
                    pendingForPaywallPhotos = 0
                    pendingForPaywallVideos = 0
                    DebugLogBuffer.log("GalleryViewModel", "startArchiving: limit reached, no items allowed, showing paywall.")
                    return
                } else {
                    selected = allowedItems
                    DebugLogBuffer.log("GalleryViewModel", "startArchiving: limit exceeded, archiving partial ${selected.size} items.")
                }
            } else {
                pendingForPaywallPhotos = 0
                pendingForPaywallVideos = 0
            }
        } else {
            pendingForPaywallPhotos = 0
            pendingForPaywallVideos = 0
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
        val trialDaysSingle = remoteConfigManager.freeTrialDays.value
        if (limitRepository.shouldApplyLimits(trialDaysSingle, getApplication())) {
            val isVideo = item.mimeType.startsWith("video/")
            val totalProjectedPhotos = limitRepository.photosArchivedCount + if (!isVideo) 1 else 0
            val totalProjectedVideos = limitRepository.videosArchivedCount + if (isVideo) 1 else 0
            val maxPhotos = remoteConfigManager.maxPhotos.value
            val maxVideos = remoteConfigManager.maxVideos.value

            if (totalProjectedPhotos > maxPhotos || totalProjectedVideos > maxVideos) {
                val missingPhotos = if (!isVideo) 1 else 0
                val missingVideos = if (isVideo) 1 else 0
                showPaywall(missingPhotos, missingVideos)
                DebugLogBuffer.log("GalleryViewModel", "archiveSingleFile: limit reached, showing paywall.")
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

    val deviceDeleteSender = mediaOperationInteractor.deviceDeleteSender

    private var pendingForPaywallPhotos = 0
    private var pendingForPaywallVideos = 0
    private var pendingItemsToDelete: List<MediaItem>? = null

    // ─── Delete state ───

    val pendingDelete: StateFlow<List<MediaItem>?> = mediaOperationInteractor.pendingDelete

    fun requestDeleteSelected() {
        val selected = mediaItems.value.filter { it.id in selectionManager.selectedIds.value }
        mediaOperationInteractor.requestDelete(selected)
    }

    fun confirmDelete() {
        selectionManager.clearSelection()
        mediaOperationInteractor.confirmDelete()
    }

    fun dismissDelete() = mediaOperationInteractor.dismissDelete()

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


    val uiState: StateFlow<GalleryUiState> = combine(
        listOf(
            displayManager.groupedMediaItems,
            displayManager.archivedGroupedItems,
            archiveYearGroups,
            mediaItems,
            otgManager.activeArchiveUuid,
            displayManager.deviceSortMode,
            displayManager.archiveSortMode,
            archivingItemIds,
            _restoringItemIds,
            copiedItemIds,
            photosArchivedCount,
            videosArchivedCount,
            isPremiumUnlocked,
            isSilentSyncingFlow,
            syncState,
            mediaOperationInteractor.isSharingPreparing,
            otgManager.isCheckingConnection,
            gridColumnsCount,
            otgManager.archiveSize,
            otgDirectoryDisplayName,
            thumbnailManager.isSyncingThumbnails,
            missingThumbnailsCount,
            isStorageLow,
            _activeDialog,
            pendingDelete
        )
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        GalleryUiState(
            groupedItems = args[0] as List<GalleryItem>,
            archivedGroupedItems = args[1] as List<GalleryItem>,
            archiveYearGroups = args[2] as List<YearGroup>,
            mediaItems = args[3] as List<MediaItem>,
            activeArchiveUuid = args[4] as String?,
            deviceSortMode = args[5] as DeviceSortMode,
            archiveSortMode = args[6] as ArchiveSortMode,
            archivingItemIds = args[7] as Set<String>,
            restoringItemIds = args[8] as Set<String>,
            copiedItemIds = args[9] as Set<String>,
            photosArchivedCount = args[10] as Int,
            videosArchivedCount = args[11] as Int,
            isPremiumUnlocked = args[12] as Boolean,
            isSilentSyncing = args[13] as Boolean,
            syncState = args[14] as String?,
            isSharingPreparing = args[15] as Boolean,
            isCheckingConnection = args[16] as Boolean,
            gridColumnsCount = args[17] as Int,
            physicalArchiveSize = args[18] as Long,
            otgDirectoryDisplayName = args[19] as String?,
            isSyncingThumbnails = args[20] as Boolean,
            missingThumbnailsCount = args[21] as Int,
            isStorageLow = args[22] as Boolean,
            activeDialog = args[23] as AppDialog?,
            pendingDelete = args[24] as List<MediaItem>?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GalleryUiState()
    )

    init {
        viewModelScope.launch {
        // uiState logic is now driven by combine() above
        }
        viewModelScope.launch {
            syncHelper.operationCompleteEvent.collect {
                otgManager.updateArchiveSize()
                otgManager.setCheckingConnection(false)
            }
        }
        
        viewModelScope.launch {
            syncHelper.archiveSuccessEvent.collect { items ->
                val newPhotos = items.count { !it.mimeType.startsWith("video/") }
                val newVideos = items.count { it.mimeType.startsWith("video/") }
                if (newPhotos > 0) {
                    limitRepository.photosArchivedCount += newPhotos
                }
                if (newVideos > 0) {
                    limitRepository.videosArchivedCount += newVideos
                }

                // Show Success Dialog or Paywall
                val freedSpaceBytes = items.sumOf { it.size.toLong() }
                val currentFreeSpaceBytes = android.os.Environment.getDataDirectory().usableSpace
                val totalSpaceBytes = android.os.Environment.getDataDirectory().totalSpace
                
                // Analytics
                val freedMb = freedSpaceBytes / (1024 * 1024)
                AppMetrica.reportEvent("archive_session_end", """{"freed_mb": $freedMb}""")
                if (limitRepository.trustLevel == 0) {
                    val profile = UserProfile.newBuilder().apply(Attribute.customNumber("trust_level").withValue(1.0)).build()
                    AppMetrica.reportUserProfile(profile)
                    limitRepository.trustLevel = 1
                }
                
                if (pendingForPaywallPhotos > 0 || pendingForPaywallVideos > 0) {
                    pendingItemsToDelete = items
                    showPaywall(pendingForPaywallPhotos, pendingForPaywallVideos)
                    pendingForPaywallPhotos = 0
                    pendingForPaywallVideos = 0
                } else {
                    _activeDialog.value = AppDialog.Success(SuccessDialogData(freedSpaceBytes, currentFreeSpaceBytes, totalSpaceBytes))
                    mediaOperationInteractor.startDeletingWithPermissionCheck(items)
                }
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
}
