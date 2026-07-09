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
    private val isBusy: () -> Boolean = { false }
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

    private val _showFirstLaunchDialog = MutableStateFlow(false)
    val showFirstLaunchDialog: StateFlow<Boolean> = _showFirstLaunchDialog.asStateFlow()

    private val _showUnknownDriveDialog = MutableStateFlow(false)
    val showUnknownDriveDialog: StateFlow<Boolean> = _showUnknownDriveDialog.asStateFlow()

    private val _showUnreadableOtgDialog = MutableStateFlow(false)
    val showUnreadableOtgDialog: StateFlow<Boolean> = _showUnreadableOtgDialog.asStateFlow()

    private val _showWriteProtectedRootDialog = MutableStateFlow(false)
    val showWriteProtectedRootDialog: StateFlow<Boolean> = _showWriteProtectedRootDialog.asStateFlow()

    private val _archiveSize = MutableStateFlow(0L)
    val archiveSize: StateFlow<Long> = _archiveSize.asStateFlow()

    private val _otgDirectoryUri = MutableStateFlow<Uri?>(null)
    val otgDirectoryUri: StateFlow<Uri?> = _otgDirectoryUri.asStateFlow()

    private val _deviceDirectoryUri = MutableStateFlow<Uri?>(null)
    val deviceDirectoryUri: StateFlow<Uri?> = _deviceDirectoryUri.asStateFlow()

    private val _showLocalFolderDialog = MutableStateFlow(false)
    val showLocalFolderDialog: StateFlow<Boolean> = _showLocalFolderDialog.asStateFlow()

    private val _isCheckingConnection = MutableStateFlow(false)
    val isCheckingConnection: StateFlow<Boolean> = _isCheckingConnection.asStateFlow()

    private val _showConnectionErrorBanner = MutableStateFlow(false)
    val showConnectionErrorBanner: StateFlow<Boolean> = _showConnectionErrorBanner.asStateFlow()

    private val _showEjectSuccessDialog = MutableStateFlow(false)
    val showEjectSuccessDialog: StateFlow<Boolean> = _showEjectSuccessDialog.asStateFlow()

    private val _showNamingDialog = MutableStateFlow<Uri?>(null)
    val showNamingDialog: StateFlow<Uri?> = _showNamingDialog.asStateFlow()

    private val _activeArchiveUuid = MutableStateFlow<String?>(null)
    val activeArchiveUuid: StateFlow<String?> = _activeArchiveUuid.asStateFlow()

    // ─── Internal state ───

    private var driveErrorCount = 0
    private val MAX_DRIVE_ERRORS = 3
    private var wasPhysicalConnected = false
    /** Флаг: приветственный диалог уже был показан в этой сессии (или был отклонён). */
    private var firstLaunchHandled = false
    private var unknownDriveDialogHandled = false
    private var isEjectedButStillPluggedIn = false
    private var isVerifying = false

    // ─── Public API ───

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
            var unreadableOtgDialogHandled = false
            while (true) {
                val usbPhysicallyConnected = withContext(Dispatchers.IO) { isUsbStoragePhysicallyConnected() }
                val otgPluggedIn = usbPhysicallyConnected || withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
                _physicalConnected.value = otgPluggedIn

                if (!usbPhysicallyConnected) {
                    unreadableOtgDialogHandled = false
                }

                if (!otgPluggedIn) {
                    unknownDriveDialogHandled = false
                    firstLaunchHandled = false
                    _showConnectionErrorBanner.value = false
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

                // Update archive size only when transitioning TO known connected
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
                    _showFirstLaunchDialog.value = true
                    firstLaunchHandled = true
                }

                // Check for unreadable OTG at startup
                if (usbPhysicallyConnected && !otgPluggedIn && _otgDirectoryUri.value == null) {
                    if (!unreadableOtgDialogHandled) {
                        _showUnreadableOtgDialog.value = true
                        unreadableOtgDialogHandled = true
                    }
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
        _showFirstLaunchDialog.value = false  // закрываем диалог, если он ещё виден

        scope.launch {
            val dir = withContext(Dispatchers.IO) {
                by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, uri, createIfNotExist = true)
            }
            if (dir == null) {
                return@launch
            }

            val uuidPair = withContext(Dispatchers.IO) { readOtgUuidFile(dir) }
            if (uuidPair != null) {
                val uuid = uuidPair.first
                val name = uuidPair.second
                
                withContext(Dispatchers.IO) {
                    db.archiveDao().insert(ArchiveEntity(uuid, name, System.currentTimeMillis(), System.currentTimeMillis()))
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
                _showNamingDialog.value = uri
            }
        }
    }

    fun saveOtgArchive(uri: Uri, name: String) {
        _showNamingDialog.value = null
        _isCheckingConnection.value = true
        scope.launch {
            val dir = withContext(Dispatchers.IO) {
                by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, uri, createIfNotExist = true)
            }
            if (dir == null) {
                _isCheckingConnection.value = false
                return@launch
            }

            val uuid = java.util.UUID.randomUUID().toString()
            val success = withContext(Dispatchers.IO) { writeOtgUuidFile(dir, uuid, name) }
            
            if (success) {
                withContext(Dispatchers.IO) {
                    db.archiveDao().insert(ArchiveEntity(uuid, name, System.currentTimeMillis(), System.currentTimeMillis()))
                    db.mediaDao().migrateLegacyArchiveUuid(uuid)
                }
                _activeArchiveUuid.value = uuid
                prefs.edit()
                    .putString("active_archive_uuid", uuid)
                    .putString(PREF_OTG_URI, uri.toString())
                    .apply()
                _otgDirectoryUri.value = uri
                
                _physicalConnected.value = withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
                val newStatus = withContext(Dispatchers.IO) { computeDriveStatus() }
                _status.value = newStatus
                updateArchiveSize()
                
                if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED && !syncHelper.archiveState.value.isArchiving && !syncHelper.isSilentSyncing) {
                    syncHelper.silentSyncArchive(_otgDirectoryUri.value)
                    refreshCacheStats()
                }
            }
            _isCheckingConnection.value = false
        }
    }

    fun dismissNamingDialog() {
        _showNamingDialog.value = null
    }

    fun setCheckingConnection(value: Boolean) {
        _isCheckingConnection.value = value
    }

    fun showLocalFolderPrompt() {
        _showLocalFolderDialog.value = true
    }

    /** Called from ViewModel when user selects local device folder via SAF. */
    fun onDeviceUriSelected(uri: Uri) {
        _deviceDirectoryUri.value = uri
        _showLocalFolderDialog.value = false
        prefs.edit().putString(PREF_DEVICE_URI, uri.toString()).apply()
    }

    fun dismissLocalFolderDialog() {
        _showLocalFolderDialog.value = false
    }

    fun dismissUnreadableOtgDialog() {
        _showUnreadableOtgDialog.value = false
    }

    fun showWriteProtectedRootDialog() {
        _showWriteProtectedRootDialog.value = true
    }

    fun dismissWriteProtectedRootDialog() {
        _showWriteProtectedRootDialog.value = false
    }

    /** Called when user explicitly ejects / removes the drive reference. */
    fun onEject() {
        // Do NOT release permissions or clear URIs! We want to remember the drive.
        // We just mark it as logically disconnected until physical replug.
        isEjectedButStillPluggedIn = true
        _showEjectSuccessDialog.value = true
        _showUnreadableOtgDialog.value = false

        // Update the status so the UI thinks it's disconnected immediately
        scope.launch {
            _status.value = DriveStatus.KNOWN_DRIVE_DISCONNECTED
            _archiveSize.value = 0L
        }
    }

    fun dismissEjectSuccessDialog() {
        _showEjectSuccessDialog.value = false
        isEjectedButStillPluggedIn = false
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
                _showConnectionErrorBanner.value = false
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
        _showFirstLaunchDialog.value = false


        // Не сбрасываем firstLaunchHandled — он сбрасывается в onPhysicalConnectionChanged
        // при реальном отключении/подключении флешки (если URI не выбран).
    }

    fun dismissUnknownDriveDialog() {
        _showUnknownDriveDialog.value = false
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
        _showUnknownDriveDialog.value = false
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
        _showConnectionErrorBanner.value = false
        try {
            val startTime = System.currentTimeMillis()
            val timeoutMs = 8000L // 8 seconds timeout
            var newStatus = DriveStatus.KNOWN_DRIVE_DISCONNECTED
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val usbPhysicallyConnected = withContext(Dispatchers.IO) { isUsbStoragePhysicallyConnected() }
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

            if (newStatus == DriveStatus.KNOWN_DRIVE_DISCONNECTED && _physicalConnected.value) {
                _showConnectionErrorBanner.value = true
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
                    if (!unknownDriveDialogHandled) {
                        _showUnknownDriveDialog.value = true
                        unknownDriveDialogHandled = true
                    }
                    DriveStatus.UNKNOWN_DRIVE_CONNECTED
                } else {
                    _showUnknownDriveDialog.value = false
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
                val dir = by.w6.my1drive.utils.OtgFolderResolver.getArchiveDir(application, savedUri, createIfNotExist = false)
                if (dir != null && dir.exists()) {
                    val uuidPair = readOtgUuidFile(dir)
                    val currentActiveUuid = _activeArchiveUuid.value
                    if (uuidPair != null) {
                        val knownArchive = db.archiveDao().getById(uuidPair.first)
                        if (knownArchive != null) {
                            if (currentActiveUuid != uuidPair.first) {
                                _activeArchiveUuid.value = uuidPair.first
                                prefs.edit().putString("active_archive_uuid", uuidPair.first).apply()
                            }
                            db.archiveDao().insert(knownArchive.copy(lastConnected = System.currentTimeMillis()))
                            db.mediaDao().migrateLegacyArchiveUuid(uuidPair.first)

                            driveErrorCount = 0
                            _showUnknownDriveDialog.value = false
                            DriveStatus.KNOWN_DRIVE_CONNECTED
                        } else if (currentActiveUuid == null) {
                            _activeArchiveUuid.value = uuidPair.first
                            prefs.edit().putString("active_archive_uuid", uuidPair.first).apply()
                            db.archiveDao().insert(ArchiveEntity(uuidPair.first, uuidPair.second, System.currentTimeMillis(), System.currentTimeMillis()))
                            db.mediaDao().migrateLegacyArchiveUuid(uuidPair.first)

                            driveErrorCount = 0
                            _showUnknownDriveDialog.value = false
                            DriveStatus.KNOWN_DRIVE_CONNECTED
                        } else {
                            if (!unknownDriveDialogHandled) {
                                _showUnknownDriveDialog.value = true
                                unknownDriveDialogHandled = true
                            }
                            DriveStatus.UNKNOWN_DRIVE_CONNECTED
                        }
                    } else {
                        if (!unknownDriveDialogHandled) {
                            _showUnknownDriveDialog.value = true
                            unknownDriveDialogHandled = true
                        }
                        DriveStatus.UNKNOWN_DRIVE_CONNECTED
                    }
                } else {
                    driveErrorCount = 0
                    _showUnknownDriveDialog.value = false
                    DriveStatus.KNOWN_DRIVE_CONNECTED
                }
            } else {
                _showUnknownDriveDialog.value = false
                DriveStatus.KNOWN_DRIVE_DISCONNECTED
            }
        } catch (e: Exception) {
            _showUnknownDriveDialog.value = false
            DriveStatus.KNOWN_DRIVE_DISCONNECTED
        }
    }

    private fun readOtgUuidFile(dir: DocumentFile): Pair<String, String>? {
        val file = dir.findFile(".my1drive_uuid") ?: dir.findFile(".my1drive_uuid.txt") ?: return null
        return try {
            application.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                val uuid = reader.readLine()?.trim() ?: ""
                val name = reader.readLine()?.trim() ?: ""
                if (uuid.isNotEmpty()) {
                    Pair(uuid, name.ifEmpty { "USB-накопитель" })
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeOtgUuidFile(dir: DocumentFile, uuid: String, name: String): Boolean {
        try {
            val file = dir.findFile(".my1drive_uuid") ?: dir.findFile(".my1drive_uuid.txt") ?: dir.createFile("text/plain", ".my1drive_uuid") ?: return false
            application.contentResolver.openOutputStream(file.uri, "w")?.use { outputStream ->
                val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(outputStream))
                writer.write(uuid)
                writer.newLine()
                writer.write(name)
                writer.flush()
            }
            return true
        } catch (e: Exception) {
            by.w6.my1drive.utils.DebugLogBuffer.log("OtgConnMgr", "Failed to write uuid file: ${e.localizedMessage}")
            return false
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
        val path = uri.path ?: return null

        // Try /document/ segment first (more specific, for subfolder URIs)
        val docSegment = path.substringAfter("/document/", "")
        if (docSegment.isNotEmpty()) {
            val rawId = docSegment.substringBefore(":")
            if (rawId.isNotEmpty() && !rawId.contains("/")) return rawId
        }

        // Fallback to /tree/ segment
        val treeSegment = path.substringAfter("/tree/", "")
        if (treeSegment.isNotEmpty()) {
            val rawId = treeSegment.substringBefore(":")
            if (rawId.isNotEmpty() && !rawId.contains("/")) return rawId
        }

        return null
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