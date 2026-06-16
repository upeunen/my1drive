package by.w6.my1drive.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
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
    private val refreshCacheStats: () -> Unit = {}
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

    private val _archiveSize = MutableStateFlow(0L)
    val archiveSize: StateFlow<Long> = _archiveSize.asStateFlow()

    private val _otgDirectoryUri = MutableStateFlow<Uri?>(null)
    val otgDirectoryUri: StateFlow<Uri?> = _otgDirectoryUri.asStateFlow()

    private val _deviceDirectoryUri = MutableStateFlow<Uri?>(null)
    val deviceDirectoryUri: StateFlow<Uri?> = _deviceDirectoryUri.asStateFlow()

    private val _showLocalFolderDialog = MutableStateFlow(false)
    val showLocalFolderDialog: StateFlow<Boolean> = _showLocalFolderDialog.asStateFlow()

    // ─── Internal state ───

    private var driveErrorCount = 0
    private val MAX_DRIVE_ERRORS = 3
    private var wasPhysicalConnected = false
    /** Флаг: приветственный диалог уже был показан в этой сессии (или был отклонён). */
    private var firstLaunchHandled = false

    // ─── Public API ───

    /**
     * Start polling loop. Should be called once from init scope.
     * @param savedOtgUri previously persisted OTG directory URI (nullable)
     * @param savedDeviceUri previously persisted local device directory URI (nullable)
     */
    fun start(savedOtgUri: Uri?, savedDeviceUri: Uri?) {
        _otgDirectoryUri.value = savedOtgUri
        _deviceDirectoryUri.value = savedDeviceUri
        scope.launch {
            var previousStatus: DriveStatus? = null
            while (true) {
                val newStatus = withContext(Dispatchers.IO) { computeDriveStatus() }
                if (newStatus != _status.value) {
                    _status.value = newStatus
                }

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
                val otgPluggedIn = withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
                _physicalConnected.value = otgPluggedIn
                if (otgPluggedIn && !firstLaunchHandled && _otgDirectoryUri.value == null) {
                    _showFirstLaunchDialog.value = true
                    firstLaunchHandled = true
                }
                wasPhysicalConnected = otgPluggedIn

                // Trigger silent sync when transitioning to KNOWN_DRIVE_CONNECTED
                if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED &&
                    previousStatus != DriveStatus.KNOWN_DRIVE_CONNECTED &&
                    !syncHelper.archiveState.value.isArchiving &&
                    !syncHelper.isSilentSyncing
                ) {
                    syncHelper.silentSyncArchive(_otgDirectoryUri.value)
                    refreshCacheStats()
                }

                previousStatus = newStatus
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Called from ViewModel when user selects a folder via SAF. */
    fun onOtgUriSelected(uri: Uri) {
        _otgDirectoryUri.value = uri
        _showFirstLaunchDialog.value = false  // закрываем диалог, если он ещё виден
        prefs.edit().putString(PREF_OTG_URI, uri.toString()).apply()

        // Сразу после выбора папки OTG, если еще не выбрана папка на устройстве,
        // показываем диалог выбора папки устройства
        if (_deviceDirectoryUri.value == null) {
            _showLocalFolderDialog.value = true
        }

        scope.launch {
            _physicalConnected.value = withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
            _status.value = withContext(Dispatchers.IO) { computeDriveStatus() }
            updateArchiveSize()
            if (!syncHelper.archiveState.value.isArchiving && !syncHelper.isSilentSyncing) {
                syncHelper.silentSyncArchive(_otgDirectoryUri.value)
                refreshCacheStats()
            }
        }
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

    /** Called when user explicitly ejects / removes the drive reference. */
    fun onEject() {
        _otgDirectoryUri.value?.let { uri ->
            try {
                application.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        _deviceDirectoryUri.value?.let { uri ->
            try {
                application.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        _otgDirectoryUri.value = null
        _deviceDirectoryUri.value = null
        prefs.edit().remove(PREF_OTG_URI).remove(PREF_DEVICE_URI).apply()
        _status.value = DriveStatus.NO_URI_CONFIGURED
        _archiveSize.value = 0L
        // Сбрасываем флаг, чтобы при следующем подключении флешки диалог показался снова
        firstLaunchHandled = false
    }

    /** Called from BroadcastReceiver when physical USB connection changes. */
    fun onPhysicalConnectionChanged() {
        scope.launch {
            val wasConnected = _physicalConnected.value
            val isConnected = withContext(Dispatchers.IO) { isAnyOtgDrivePresent() }
            _physicalConnected.value = isConnected
            _status.value = withContext(Dispatchers.IO) { computeDriveStatus() }
            // Если URI не выбран — сбрасываем флаг, чтобы при следующем подключении
            // диалог показался снова (логика перетыкания флешки)
            if (_otgDirectoryUri.value == null) {
                firstLaunchHandled = false
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
        _otgDirectoryUri.value = null
        prefs.edit().remove(PREF_OTG_URI).apply()
        scope.launch(Dispatchers.IO) {
            db.clearAllTables()
        }
        _status.value = DriveStatus.NO_URI_CONFIGURED
        _archiveSize.value = 0L
        _showUnknownDriveDialog.value = false
        // Сбросить флаги, чтобы при подключённой флешке снова показать приветствие
        wasPhysicalConnected = false
        firstLaunchHandled = false
    }

    // ─── Internal helpers ───

    private suspend fun computeDriveStatus(): DriveStatus {
        val savedUri = _otgDirectoryUri.value ?: return DriveStatus.NO_URI_CONFIGURED

        val isPhysicallyConnected = isOtgUriPhysicallyConnected(savedUri)

        if (!isPhysicallyConnected) {
            return DriveStatus.KNOWN_DRIVE_DISCONNECTED
        }

        return try {
            val docFile = DocumentFile.fromTreeUri(application, savedUri)
            if (docFile != null && docFile.exists() && docFile.canRead()) {
                driveErrorCount = 0
                _showUnknownDriveDialog.value = false
                DriveStatus.KNOWN_DRIVE_CONNECTED
            } else {
                // Different drive is connected (UUID matches but content differs)
                _showUnknownDriveDialog.value = true
                DriveStatus.UNKNOWN_DRIVE_CONNECTED
            }
        } catch (e: Exception) {
            driveErrorCount++
            if (driveErrorCount >= MAX_DRIVE_ERRORS) {
                _otgDirectoryUri.value = null
                prefs.edit().remove(PREF_OTG_URI).apply()
                driveErrorCount = 0
            }
            _showUnknownDriveDialog.value = true
            DriveStatus.UNKNOWN_DRIVE_CONNECTED
        }
    }

    /** Checks if any removable (OTG) drive is physically connected. */
    private fun isAnyOtgDrivePresent(): Boolean {
        return try {
            val storageManager = application.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return false
            storageManager.storageVolumes.any { volume ->
                volume.isRemovable && volume.state == Environment.MEDIA_MOUNTED
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isOtgUriPhysicallyConnected(uri: Uri): Boolean {
        try {
            val path = uri.path ?: return false
            val treeSegment = path.substringAfter("/tree/", "")
            if (treeSegment.isEmpty()) return false
            val rawId = treeSegment.substringBefore(":")
            if (rawId.isEmpty()) return false

            val storageManager = application.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return false
            val volumes = storageManager.storageVolumes

            if (rawId.equals("primary", ignoreCase = true)) {
                return volumes.firstOrNull { it.isPrimary }?.state == Environment.MEDIA_MOUNTED
            }

            for (volume in volumes) {
                val volUuid = volume.uuid
                if (volUuid != null && volUuid.equals(rawId, ignoreCase = true)) {
                    return volume.state == Environment.MEDIA_MOUNTED
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /** Calculates physical file sizes on the OTG drive (excluding metadata files). */
    private fun calculateArchiveSize(): Long {
        val uri = _otgDirectoryUri.value ?: return 0L
        return try {
            val dir = DocumentFile.fromTreeUri(application, uri)
            if (dir != null && dir.exists()) {
                dir.listFiles()
                    .filter { !it.isDirectory && it.name != ".my1drive_uuid" }
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
}