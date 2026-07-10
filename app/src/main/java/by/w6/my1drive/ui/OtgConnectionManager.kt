package by.w6.my1drive.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.hardware.usb.UsbManager
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.data.local.ArchiveEntity
import by.w6.my1drive.utils.OtgFolderResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages OTG drive connection state, polls physical status,
 * and provides state flows for the UI layer.
 *
 * States (only one active at a time):
 * - NO_URI_CONFIGURED — URI not selected; may or may not have a physical drive.
 * - KNOWN_DRIVE_CONNECTED — Same drive, URI saved, files accessible.
 * - KNOWN_DRIVE_DISCONNECTED — Same drive, URI saved, but physically removed.
 * - UNKNOWN_DRIVE_CONNECTED — Different drive plugged in (UUID mismatch).
 */
class OtgConnectionManager(
    private val application: Application,
    private val prefs: android.content.SharedPreferences,
    private val db: by.w6.my1drive.data.local.AppDatabase,
    private val syncHelper: ArchiveSyncHelper,
    private val scope: CoroutineScope,
    private val refreshCacheStats: () -> Unit = {},
    private val isBusy: () -> Boolean = { false },
    private val onShowFirstLaunchDialog: (Boolean) -> Unit = {},
    private val onShowUnknownDriveDialog: (Boolean) -> Unit = {},
    private val onShowUnreadableOtgDialog: (Boolean) -> Unit = {},
    private val onShowWriteProtectedRootDialog: (Boolean) -> Unit = {},
    private val onShowLocalFolderDialog: (Boolean) -> Unit = {},
    private val onShowNamingDialog: (Uri?) -> Unit = {}
) {
    companion object {
        private const val PREF_OTG_URI = "otg_directory_uri"
        private const val PREF_DEVICE_URI = "device_directory_uri"
        private const val POLL_INTERVAL_MS = 3000L
    }

    // ─── Exposed Flows ───

    private val _status = MutableStateFlow(DriveStatus.NO_URI_CONFIGURED)
    val status: StateFlow<DriveStatus> = _status.asStateFlow()

    private val _physicalConnected = MutableStateFlow(false)
    val physicalConnected: StateFlow<Boolean> = _physicalConnected.asStateFlow()


    private val _archiveSize = MutableStateFlow(0L)
    val archiveSize: StateFlow<Long> = _archiveSize.asStateFlow()

    private val _otgDirectoryUri = MutableStateFlow<Uri?>(null)
    val otgDirectoryUri: StateFlow<Uri?> = _otgDirectoryUri.asStateFlow()

    private val _deviceDirectoryUri = MutableStateFlow<Uri?>(null)
    val deviceDirectoryUri: StateFlow<Uri?> = _deviceDirectoryUri.asStateFlow()


    private val _isCheckingConnection = MutableStateFlow(false)
    val isCheckingConnection: StateFlow<Boolean> = _isCheckingConnection.asStateFlow()
    private val _showEjectSuccessDialog = MutableStateFlow(false)
    val showEjectSuccessDialog: StateFlow<Boolean> = _showEjectSuccessDialog.asStateFlow()


    private val _activeArchiveUuid = MutableStateFlow<String?>(null)
    val activeArchiveUuid: StateFlow<String?> = _activeArchiveUuid.asStateFlow()

    // ─── Internal state ───

    private var driveErrorCount = 0
    private val MAX_DRIVE_ERRORS = 3
    private var wasPhysicalConnected = false
    /** Флаг: приветственный диалог уже был показан в этой сессии (или был отклонён). */
    private var firstLaunchHandled = false
    private var unknownDriveDialogHandled = false
    private val scannedUris = mutableSetOf<String>()
    private var isEjectedButStillPluggedIn = false
    private var isVerifying = false

    private var isPollingPaused = false

    fun pausePolling() {
        isPollingPaused = true
    }

    fun resumePolling() {
        isPollingPaused = false
    }

    /**
     * Start polling loop. Should be called once from init scope.
     * @param savedOtgUri previously persisted OTG directory URI (nullable)
     * @param savedDeviceUri previously persisted local device directory URI (nullable)
     */
    fun start(savedOtgUri: Uri?, savedDeviceUri: Uri?) {
        _otgDirectoryUri.value = savedOtgUri
        _deviceDirectoryUri.value = savedDeviceUri
        _activeArchiveUuid.value = prefs.getString("active_archive_uuid", null)
        scope.launch {
            var previousStatus: DriveStatus? = null
            var firstCheck = true
            while (true) {
                if (isPollingPaused) {
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val usbPhysicallyConnected = withContext(Dispatchers.IO) { isUsbStoragePhysicallyConnected() }
                val otgPluggedIn = usbPhysicallyConnected || withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
                _physicalConnected.value = otgPluggedIn

                if (!usbPhysicallyConnected) {
                    // Do nothing
                }

                if (!otgPluggedIn) {
                    unknownDriveDialogHandled = false
                    firstLaunchHandled = false
                    if (isEjectedButStillPluggedIn) {
                        isEjectedButStillPluggedIn = false
                        _showEjectSuccessDialog.value = false
                    }
                }

                // Show preloader only when transitioning from physically disconnected to connected, or at startup
                val isTransitionToConnected = otgPluggedIn && !wasPhysicalConnected
                val isVerifyingNeeded = (firstCheck || isTransitionToConnected) && _otgDirectoryUri.value != null
                if (isVerifyingNeeded) {
                    verifyConnectionAndWait()
                } else {
                    val computed = withContext(Dispatchers.IO) { computeDriveStatus() }
                    if (computed != _status.value) {
                        _status.value = computed
                    }
                }

                val newStatus = _status.value

                // Update archive size and clear error banner only when transitioning TO known connected
                if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED) {
                    if (previousStatus != DriveStatus.KNOWN_DRIVE_CONNECTED) {
                        updateArchiveSize()
                    }
                }
                // IMPORTANT: Do NOT reset archive size on disconnect —
                // keep last known value (as per spec C)

                // First-launch detection: показываем приветствие если URI не выбран,
                // а OTG-флешка физически подключена. Показывается один раз за сессию
                // (до dismiss или выбора папки).
                // Всегда проверяем физическое наличие OTG, даже без сохранённого URI.
                if (otgPluggedIn && !firstLaunchHandled && _otgDirectoryUri.value == null && !isEjectedButStillPluggedIn) {
                    onShowFirstLaunchDialog(true)
                    firstLaunchHandled = true
                }

                wasPhysicalConnected = otgPluggedIn

                // Trigger silent sync when transitioning to KNOWN_DRIVE_CONNECTED
                // on connection transition
                if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED &&
                    previousStatus != DriveStatus.KNOWN_DRIVE_CONNECTED &&
                    !syncHelper.archiveState.value.isArchiving &&
                    !syncHelper.isSilentSyncing
                ) {
                    syncHelper.silentSyncArchive(_otgDirectoryUri.value)
                    refreshCacheStats()
                }

                firstCheck = false
                previousStatus = newStatus
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Called from ViewModel when user selects a folder via SAF. */
    fun onOtgUriSelected(uri: Uri) {
        by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "onOtgUriSelected: uri=$uri, path=${uri.path}, authority=${uri.authority}")
        onShowFirstLaunchDialog(false)  // закрываем диалог, если он ещё виден

        scope.launch {
            var uuid = OtgFolderResolver.extractVolumeId(uri) ?: uri.toString().hashCode().toString()
            
            var knownArchive = withContext(Dispatchers.IO) { db.archiveDao().getById(uuid) }
            
            if (knownArchive == null) {
                val recovered = withContext(Dispatchers.IO) {
                    OtgFolderResolver.scanAndRecoverArchive(application, uri)
                }
                if (recovered != null) {
                    knownArchive = recovered
                    uuid = recovered.uuid
                }
            }
            
            val dir = withContext(Dispatchers.IO) {
                OtgFolderResolver.getArchiveDir(application, uri, createIfNotExist = false)
            }
            
            if (knownArchive != null && dir != null && dir.exists()) {
                withContext(Dispatchers.IO) {
                    db.archiveDao().insert(knownArchive.copy(lastConnected = System.currentTimeMillis()))
                    db.mediaDao().migrateLegacyArchiveUuid(uuid)
                }
                
                _activeArchiveUuid.value = uuid
                prefs.edit()
                    .putString("active_archive_uuid", uuid)
                    .putString(PREF_OTG_URI, uri.toString())
                    .apply()
                    
                _otgDirectoryUri.value = uri
                _isCheckingConnection.value = true
                
                _physicalConnected.value = withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
                val newStatus = withContext(Dispatchers.IO) { computeDriveStatus() }
                _status.value = newStatus
                updateArchiveSize()
                _isCheckingConnection.value = false
                
                if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED && !syncHelper.archiveState.value.isArchiving && !syncHelper.isSilentSyncing) {
                    syncHelper.silentSyncArchive(_otgDirectoryUri.value)
                    refreshCacheStats()
                }
            } else {
                onShowNamingDialog(uri)
            }
        }
    }

    fun saveOtgArchive(uri: Uri, name: String) {
        onShowNamingDialog(null)
        _isCheckingConnection.value = true
        scope.launch {
            val uuid = OtgFolderResolver.extractVolumeId(uri) ?: uri.toString().hashCode().toString()
            val folderName = "Arhiv-$name"

            withContext(Dispatchers.IO) {
                // 1. Insert/register the ArchiveEntity in Room first
                db.archiveDao().insert(
                    ArchiveEntity(
                        uuid = uuid,
                        name = name,
                        folderName = folderName,
                        dateCreated = System.currentTimeMillis(),
                        lastConnected = System.currentTimeMillis()
                    )
                )
                db.mediaDao().migrateLegacyArchiveUuid(uuid)
            }

            // 2. Resolve/Create the physical directory on the drive (which will now use the folderName from the DB!)
            val dir = withContext(Dispatchers.IO) {
                by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, uri, createIfNotExist = true)
            }

            if (dir == null) {
                // If directory creation failed, clean up DB entry
                withContext(Dispatchers.IO) {
                    db.archiveDao().delete(uuid)
                }
                _isCheckingConnection.value = false
                return@launch
            }

            _activeArchiveUuid.value = uuid
            prefs.edit()
                .putString("active_archive_uuid", uuid)
                .putString(PREF_OTG_URI, uri.toString())
                .apply()
            _otgDirectoryUri.value = uri
            
            // Write empty metadata immediately (creates the .my1drive_db.json file on the disk)
            withContext(Dispatchers.IO) {
                val store = by.w6.my1drive.utils.ArchiveMetadataStore(application)
                store.writeMetadata(uri, emptyList())
            }
            
            _physicalConnected.value = withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
            val newStatus = withContext(Dispatchers.IO) { computeDriveStatus() }
            _status.value = newStatus
            updateArchiveSize()
            
            if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED && !syncHelper.archiveState.value.isArchiving && !syncHelper.isSilentSyncing) {
                syncHelper.silentSyncArchive(_otgDirectoryUri.value)
                refreshCacheStats()
            }
            _isCheckingConnection.value = false
        }
    }

    fun dismissNamingDialog() {
        onShowNamingDialog(null)
    }

    fun setCheckingConnection(value: Boolean) {
        _isCheckingConnection.value = value
    }

    fun showLocalFolderPrompt() {
        onShowLocalFolderDialog(true)
    }

    /** Called from ViewModel when user selects local device folder via SAF. */
    fun onDeviceUriSelected(uri: Uri) {
        _deviceDirectoryUri.value = uri
        onShowLocalFolderDialog(false)
        prefs.edit().putString(PREF_DEVICE_URI, uri.toString()).apply()
    }

    fun dismissLocalFolderDialog() {
        onShowLocalFolderDialog(false)
    }

    fun showWriteProtectedRootDialog() {
        onShowWriteProtectedRootDialog(true)
    }

    fun dismissWriteProtectedRootDialog() {
        onShowWriteProtectedRootDialog(false)
    }

    /** Called when user explicitly ejects / removes the drive reference. */
    fun onEject() {
        // Do NOT release permissions or clear URIs! We want to remember the drive.
        // We just mark it as logically disconnected until physical replug.
        isEjectedButStillPluggedIn = true
        _showEjectSuccessDialog.value = true

        
        syncHelper.cancelOperations()

        // Update the status so the UI thinks it's disconnected immediately
        scope.launch {
            _status.value = DriveStatus.KNOWN_DRIVE_DISCONNECTED
            _archiveSize.value = 0L
        }
    }

    fun dismissEjectSuccessDialog() {
        _showEjectSuccessDialog.value = false
    }

    fun retryConnection() {
        onPhysicalConnectionChanged(isStartup = false)
    }

    /** Called from BroadcastReceiver when physical USB connection changes. */
    fun onPhysicalConnectionChanged(isStartup: Boolean = false) {
        scope.launch {
            val wasConnected = _physicalConnected.value
            val usbPhysicallyConnected = withContext(Dispatchers.IO) { isUsbStoragePhysicallyConnected() }
            val otgPluggedIn = usbPhysicallyConnected || withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
            _physicalConnected.value = otgPluggedIn

            if (!otgPluggedIn) {
                unknownDriveDialogHandled = false
                if (isEjectedButStillPluggedIn) {
                    isEjectedButStillPluggedIn = false
                    _showEjectSuccessDialog.value = false
                }
            }

            val isTransitionToConnected = otgPluggedIn && !wasConnected
            val isVerifyingNeeded = (isStartup || isTransitionToConnected) && _otgDirectoryUri.value != null

            if (isVerifyingNeeded) {
                verifyConnectionAndWait()
            } else {
                val computed = withContext(Dispatchers.IO) { computeDriveStatus() }
                if (computed != _status.value) {
                    _status.value = computed
                }
            }

            val newStatus = _status.value

            if (_otgDirectoryUri.value == null && otgPluggedIn && !wasConnected) {
                firstLaunchHandled = false
            }

            if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED && !syncHelper.archiveState.value.isArchiving && !syncHelper.isSilentSyncing) {
                syncHelper.silentSyncArchive(_otgDirectoryUri.value)
                refreshCacheStats()
            }
        }
    }

    fun dismissFirstLaunchDialog() {
        onShowFirstLaunchDialog(false)


        // Не сбрасываем firstLaunchHandled — он сбрасывается в onPhysicalConnectionChanged
        // при реальном отключении/подключении флешки (если URI не выбран).
    }

    fun dismissUnknownDriveDialog() {
        onShowUnknownDriveDialog(false)
    }

    /**
     * Called when user opts to create a new archive on an unknown drive.
     * Resets the saved URI reference. The old metadata stays in the Room cache
     * until overwritten by reading the JSON metadata from the newly attached drive.
     */
    fun createNewArchive() {
        by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "createNewArchive: start")
        _otgDirectoryUri.value = null
        prefs.edit().remove(PREF_OTG_URI).apply()
        scope.launch(Dispatchers.IO) {
            try {
                db.clearAllTables()
                by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "createNewArchive: db tables cleared")
            } catch (e: Exception) {
                by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "createNewArchive: db clear error: ${e.localizedMessage}")
            }
        }
        _status.value = DriveStatus.NO_URI_CONFIGURED
        _archiveSize.value = 0L
        onShowUnknownDriveDialog(false)
        // Сбросить флаги, чтобы при подключённой флешке снова показать приветствие
        wasPhysicalConnected = false
        firstLaunchHandled = true
        unknownDriveDialogHandled = false
        by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "createNewArchive: end")
    }
    private suspend fun verifyConnectionAndWait() {
        if (isVerifying) return
        isVerifying = true
        _isCheckingConnection.value = true
        try {
            val startTime = System.currentTimeMillis()
            val timeoutMs = 20000L // 20 seconds timeout for slow OS mounts (e.g. Android 12)
            var newStatus = DriveStatus.KNOWN_DRIVE_DISCONNECTED
            var usbPhysicallyConnected = false
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                usbPhysicallyConnected = withContext(Dispatchers.IO) { isUsbStoragePhysicallyConnected() }
                val otgPluggedIn = usbPhysicallyConnected || withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
                _physicalConnected.value = otgPluggedIn
                
                if (!otgPluggedIn) {
                    newStatus = DriveStatus.KNOWN_DRIVE_DISCONNECTED
                    break
                }
                
                newStatus = withContext(Dispatchers.IO) { computeDriveStatus() }
                if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED || newStatus == DriveStatus.UNKNOWN_DRIVE_CONNECTED) {
                    break
                }
                
                delay(500)
            }
            
            if (newStatus != _status.value) {
                _status.value = newStatus
            }

        } finally {
            isVerifying = false
            _isCheckingConnection.value = false
        }
    }

    private suspend fun computeDriveStatus(): DriveStatus {
        if (isEjectedButStillPluggedIn) {
            return DriveStatus.KNOWN_DRIVE_DISCONNECTED
        }

        // 1. Scan persisted tree URIs to see if any known drive is physically connected
        val persistedPermissions = try {
            application.contentResolver.persistedUriPermissions
        } catch (_: Exception) { emptyList() }
        
        by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "Checking persisted permissions: size=${persistedPermissions.size}")
        
        var connectedUri: Uri? = null
        var connectedUuid: String? = null
        var connectedName: String? = null

        for (perm in persistedPermissions) {
            val uri = perm.uri
            val isReadable = try {
                val docFile = DocumentFile.fromTreeUri(application, uri)
                docFile != null && docFile.exists() && docFile.canRead()
            } catch (_: Exception) { false }
            by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "Perm URI: $uri, isReadable=$isReadable")
            if (isReadable) {
                val uuid = by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(uri) ?: uri.toString().hashCode().toString()
                val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, uri, createIfNotExist = false)
                val knownArchive = db.archiveDao().getById(uuid)
                
                if (knownArchive != null && dir != null && dir.exists()) {
                    connectedUri = uri
                    connectedUuid = uuid
                    connectedName = knownArchive.name
                    break
                }
                
                // If not in Room (e.g. app reinstalled), try JSON recovery (highly robust but slower)
                val recovered = by.w6.my1drive.utils.OtgFolderResolver.scanAndRecoverArchive(application, uri)
                if (recovered != null) {
                    connectedUri = uri
                    connectedUuid = recovered.uuid
                    connectedName = recovered.name
                    break
                }
            }
        }

        if (connectedUri != null && connectedUuid != null && connectedName != null) {
            val currentActiveUuid = _activeArchiveUuid.value
            val currentOtgUri = _otgDirectoryUri.value

            if (currentActiveUuid != connectedUuid || currentOtgUri != connectedUri) {
                _activeArchiveUuid.value = connectedUuid
                _otgDirectoryUri.value = connectedUri
                prefs.edit()
                    .putString("active_archive_uuid", connectedUuid)
                    .putString(PREF_OTG_URI, connectedUri.toString())
                    .apply()
            }

            db.archiveDao().getById(connectedUuid)?.let {
                db.archiveDao().insert(it.copy(lastConnected = System.currentTimeMillis()))
            }
            db.mediaDao().migrateLegacyArchiveUuid(connectedUuid)

            driveErrorCount = 0
            onShowUnknownDriveDialog(false)
            unknownDriveDialogHandled = false
            return DriveStatus.KNOWN_DRIVE_CONNECTED
        }

        val savedUri = _otgDirectoryUri.value ?: return DriveStatus.NO_URI_CONFIGURED

        val isPhysicallyConnected = isOtgUriPhysicallyConnected(savedUri)

        if (!isPhysicallyConnected) {
            // Fallback: UUID-based check may fail for document URIs (e.g. subfolder URIs
            // from DocumentFile.createDirectory on Samsung devices). Try DocumentFile access directly.
            val fallbackConnected = try {
                val docFile = DocumentFile.fromTreeUri(application, savedUri)
                docFile != null && docFile.exists() && docFile.canRead()
            } catch (_: Exception) { false }

            if (fallbackConnected) {
                by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "UUID check failed but DocumentFile fallback succeeded for $savedUri")
            } else {
                return if (isAnyOtgDrivePresent()) {
                    // Check if any currently mounted volume is known in Room DB
                    val mountedVolumes = try {
                        val sm = application.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                        sm.storageVolumes
                    } catch (_: Exception) { emptyList() }
                    
                    var isActuallyKnownButNotReady = false
                    for (volume in mountedVolumes) {
                        val uuid = volume.uuid
                        if (!volume.isPrimary && volume.state == Environment.MEDIA_MOUNTED && uuid != null) {
                            if (db.archiveDao().getById(uuid) != null) {
                                isActuallyKnownButNotReady = true
                                break
                            }
                        }
                    }
                    
                    if (isActuallyKnownButNotReady) {
                        // It's a known drive, SAF just hasn't made it readable yet. Keep waiting.
                        DriveStatus.KNOWN_DRIVE_DISCONNECTED
                    } else {
                        if (!unknownDriveDialogHandled) {
                            onShowUnknownDriveDialog(true)
                            unknownDriveDialogHandled = true
                        }
                        DriveStatus.UNKNOWN_DRIVE_CONNECTED
                    }
                } else {
                    onShowUnknownDriveDialog(false)
                    unknownDriveDialogHandled = false
                    DriveStatus.KNOWN_DRIVE_DISCONNECTED
                }
            }
        }

        if (isBusy()) {
            return DriveStatus.KNOWN_DRIVE_CONNECTED
        }

        return try {
            val docFile = DocumentFile.fromTreeUri(application, savedUri)
            if (docFile != null && docFile.exists() && docFile.canRead()) {
                val fallbackUuid = by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(savedUri) ?: savedUri.toString().hashCode().toString()
                var knownArchive = db.archiveDao().getById(fallbackUuid)
                var uuid = fallbackUuid
                
                if (knownArchive == null) {
                    val uriStr = savedUri.toString()
                    if (!scannedUris.contains(uriStr)) {
                        scannedUris.add(uriStr)
                        val recovered = by.w6.my1drive.utils.OtgFolderResolver.scanAndRecoverArchive(application, savedUri)
                        if (recovered != null) {
                            knownArchive = recovered
                            uuid = recovered.uuid
                        }
                    }
                }
                val currentActiveUuid = _activeArchiveUuid.value
                
                if (knownArchive != null) {
                    val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, savedUri, createIfNotExist = false)
                    if (dir != null && dir.exists()) {
                        if (currentActiveUuid != uuid) {
                            _activeArchiveUuid.value = uuid
                            prefs.edit().putString("active_archive_uuid", uuid).apply()
                        }
                        db.archiveDao().insert(knownArchive.copy(lastConnected = System.currentTimeMillis()))
                        db.mediaDao().migrateLegacyArchiveUuid(uuid)

                        driveErrorCount = 0
                        onShowUnknownDriveDialog(false)
                        unknownDriveDialogHandled = false
                        DriveStatus.KNOWN_DRIVE_CONNECTED
                    } else {
                        if (!unknownDriveDialogHandled) {
                            onShowUnknownDriveDialog(true)
                            unknownDriveDialogHandled = true
                        }
                        DriveStatus.UNKNOWN_DRIVE_CONNECTED
                    }
                } else {
                    if (!unknownDriveDialogHandled) {
                        onShowUnknownDriveDialog(true)
                        unknownDriveDialogHandled = true
                    }
                    DriveStatus.UNKNOWN_DRIVE_CONNECTED
                }
            } else {
                onShowUnknownDriveDialog(false)
                DriveStatus.KNOWN_DRIVE_DISCONNECTED
            }
        } catch (e: Exception) {
            onShowUnknownDriveDialog(false)
            DriveStatus.KNOWN_DRIVE_DISCONNECTED
        }
    }

    /** Checks if any removable (OTG) drive is physically connected. */
    private fun isAnyOtgDrivePresent(): Boolean {
        // Fallback 1: check storageVolumes
        try {
            val storageManager = application.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (storageManager != null) {
                val hasOtg = storageManager.storageVolumes.any { volume ->
                    !volume.isPrimary && volume.state == Environment.MEDIA_MOUNTED
                }
                if (hasOtg) return true
            }
        } catch (_: Exception) {}

        // Fallback 2: scan /storage directory directly (highly reliable on Samsung/custom ROMs)
        try {
            val storageDir = java.io.File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val files = storageDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isDirectory && file.canRead()) {
                            val name = file.name.lowercase()
                            if (name != "emulated" && name != "self" && name != "sdcard") {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Extract the volume UUID from a SAF URI.
     * Handles both simple tree URIs and document URIs inside tree URIs:
     *  - content://.../tree/UUID%3A              → UUID
     *  - content://.../tree/UUID%3A/document/UUID%3ASubfolder → UUID
     *  - content://.../tree/UUID%3ASubfolder      → UUID
     *
     * On Samsung devices, DocumentFile.createDirectory() returns a document URI
     * inside the tree, so we must handle the /document/ segment.
     */
    private fun extractVolumeId(uri: Uri): String? {
        return by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(uri)
    }

    private fun isOtgUriPhysicallyConnected(uri: Uri): Boolean {
        try {
            val rawId = extractVolumeId(uri)
            if (rawId == null) {
                by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "extractVolumeId returned null for URI: $uri (path=${uri.path})")
                return false
            }

            if (rawId.equals("primary", ignoreCase = true)) {
                return java.io.File("/storage/emulated/0").exists()
            }

            // Direct filesystem check (extremely reliable, bypasses StorageManager bugs)
            val directFile = java.io.File("/storage/$rawId")
            if (directFile.exists() && directFile.isDirectory && directFile.canRead()) {
                return true
            }

            val storageManager = application.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return false
            val volumes = storageManager.storageVolumes

            for (volume in volumes) {
                val volUuid = volume.uuid
                if (volUuid != null && volUuid.equals(rawId, ignoreCase = true)) {
                    return volume.state == Environment.MEDIA_MOUNTED
                }
            }

            by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "Volume UUID '$rawId' not found among mounted volumes. Available: ${volumes.map { "${it.uuid}/${it.state}" }}")
        } catch (e: Exception) {
            by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "isOtgUriPhysicallyConnected exception: ${e.localizedMessage}")
            e.printStackTrace()
        }
        return false
    }

    private fun calculateArchiveSize(): Long {
        val uri = _otgDirectoryUri.value ?: return 0L
        return try {
            val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, uri, createIfNotExist = false)
            if (dir != null && dir.exists()) {
                dir.listFiles()
                    .filter { !it.isDirectory && it.name != ".my1drive_uuid" && it.name != ".my1drive_uuid.txt" }
                    .sumOf { it.length() }
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun updateArchiveSize() {
        scope.launch(Dispatchers.IO) {
            _archiveSize.value = calculateArchiveSize()
        }
    }

    private fun isUsbStoragePhysicallyConnected(): Boolean {
        return try {
            val usbManager = application.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
            val deviceList = usbManager.deviceList
            for (device in deviceList.values) {
                for (i in 0 until device.interfaceCount) {
                    val usbInterface = device.getInterface(i)
                    if (usbInterface.interfaceClass == 8) { // USB_CLASS_MASS_STORAGE
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
