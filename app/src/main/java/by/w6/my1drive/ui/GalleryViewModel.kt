package by.w6.my1drive.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.ToneGenerator
import android.media.AudioManager
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

// ─────────────────────────────────────────────────────────────────────────────
// Drive connection status
// ─────────────────────────────────────────────────────────────────────────────

enum class DriveStatus {
    /** No OTG folder ever selected */
    NO_URI_CONFIGURED,
    /** Saved OTG folder is accessible and UUID matches the known archive */
    KNOWN_DRIVE_CONNECTED,
    /** Saved OTG folder is not accessible, no USB detected */
    KNOWN_DRIVE_DISCONNECTED,
    /** A USB drive is connected but the saved folder is not found on it */
    UNKNOWN_DRIVE_CONNECTED,
    /** A drive is connected with a DIFFERENT archive UUID — "found new archive" */
    NEW_ARCHIVE_FOUND
}

// ─────────────────────────────────────────────────────────────────────────────
// State data classes
// ─────────────────────────────────────────────────────────────────────────────

data class PendingDeleteRequest(
    val intentSender: IntentSender,
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

sealed class FormatState {
    data class Progress(val message: String) : FormatState()
    data class Success(val message: String) : FormatState()
    data class Error(val message: String) : FormatState()
}

sealed class RestoreRequest {
    object NeedFolderPicker : RestoreRequest()
    data class AskOriginalOrCustom(val items: List<MediaItem>, val originalPath: String) : RestoreRequest()
}

private const val PREFS_NAME = "my1drive_prefs"
private const val PREF_ASK_RESTORE_PATH = "ask_restore_path"
private const val PREF_USE_FOLDER_TREE = "use_folder_tree"
private const val PREF_OTG_URI = "otg_directory_uri"
private const val PREF_KNOWN_ARCHIVE_ID = "known_archive_uuid"
private const val PREF_MISSING_FILES_DISMISSED = "missing_files_dismissed"
private const val PREF_MISSING_FILES_HASH = "missing_files_hash"

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

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

    /** Current folder path within archive (null = root) */
    private val _currentArchiveFolderPath = MutableStateFlow<String?>(null)
    val currentArchiveFolderPath = _currentArchiveFolderPath.asStateFlow()

    /** List of subfolder names in the current archive folder */
    private val _archiveSubfolders = MutableStateFlow<List<String>>(emptyList())
    val archiveSubfolders = _archiveSubfolders.asStateFlow()

    /** Navigate into a subfolder */
    fun navigateToArchiveFolder(folderName: String) {
        val parent = _currentArchiveFolderPath.value
        val newPath = if (parent == null) folderName else "$parent/$folderName"
        _currentArchiveFolderPath.value = newPath
        refreshOtgFolders()
    }

    /** Go up one level */
    fun navigateUpArchiveFolder() {
        val current = _currentArchiveFolderPath.value ?: return
        val slashIdx = current.lastIndexOf('/')
        _currentArchiveFolderPath.value = if (slashIdx < 0) null else current.substring(0, slashIdx)
        refreshOtgFolders()
    }

    /** Go back to archive root */
    fun navigateToArchiveRoot() {
        _currentArchiveFolderPath.value = null
        refreshOtgFolders()
    }

    /** Refresh the list of subfolders in the current archive folder */
    fun refreshOtgFolders() {
        val uri = _otgDirectoryUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                var dir = DocumentFile.fromTreeUri(context, uri) ?: return@launch
                val currentPath = _currentArchiveFolderPath.value
                if (currentPath != null) {
                    for (segment in currentPath.split('/')) {
                        if (segment.isNotBlank()) {
                            dir = dir.findFile(segment) ?: return@launch
                        }
                    }
                }
                val folders = dir.listFiles()
                    .filter { it.isDirectory && it.name != null && it.name != ".my1drive_uuid" }
                    .map { it.name!! }
                    .sorted()
                _archiveSubfolders.value = folders
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val archivedGroupedItems: StateFlow<List<GalleryItem>> = mediaItems
        .map { list ->
            val archivedList = list.filter { it.status == MediaStatus.ARCHIVED_OTG && (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")) }
            val resultList = mutableListOf<GalleryItem>()

            // Add folder items first
            val folders = _archiveSubfolders.value
            for (folder in folders) {
                val folderPath = if (_currentArchiveFolderPath.value == null) folder else "${_currentArchiveFolderPath.value}/$folder"
                resultList.add(GalleryItem.Folder(name = folder, path = folderPath))
            }

            // Then grouped media by date
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

    // Drive connection status (replaces raw isOtgConnected boolean)
    private val _driveStatus = MutableStateFlow(DriveStatus.NO_URI_CONFIGURED)
    val driveStatus = _driveStatus.asStateFlow()

    /** Convenience: true only when the known archive drive is accessible */
    val isOtgConnected: StateFlow<Boolean> = _driveStatus
        .map { it == DriveStatus.KNOWN_DRIVE_CONNECTED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _formatState = MutableStateFlow<FormatState?>(null)
    val formatState = _formatState.asStateFlow()

    private val _syncState = MutableStateFlow<String?>(null)
    val syncState = _syncState.asStateFlow()

    // "Спрашивать куда разархивировать" setting
    private val _askRestorePath = MutableStateFlow(prefs.getBoolean(PREF_ASK_RESTORE_PATH, false))
    val askRestorePath = _askRestorePath.asStateFlow()

    // "Использовать дерево папок в архиве" setting
    private val _useFolderTree = MutableStateFlow(prefs.getBoolean(PREF_USE_FOLDER_TREE, false))
    val useFolderTree = _useFolderTree.asStateFlow()

    private val _restoreRequest = MutableStateFlow<RestoreRequest?>(null)
    val restoreRequest = _restoreRequest.asStateFlow()

    // Missing files notification (after silent auto-sync detects OTG files gone)
    private val _missingFilesNotification = MutableStateFlow<List<String>?>(null)
    val missingFilesNotification = _missingFilesNotification.asStateFlow()

    // Auto-sync added count notification
    private val _autoSyncAddedCount = MutableStateFlow(0)
    val autoSyncAddedCount = _autoSyncAddedCount.asStateFlow()

    private var pendingRestoreItems: List<MediaItem> = emptyList()
    private val pendingDeletesQueue = mutableListOf<ArchivedInfo>()
    private var lastArchiveSummary: String? = null

    // Guard to prevent multiple concurrent silent syncs
    private var isSilentSyncing = false

    // UUID of the archive on the connected drive (read once per connection)
    private var _newArchiveId = MutableStateFlow<String?>(null)
    val newArchiveId = _newArchiveId.asStateFlow()

    // Cache stats: Pair(sizeBytes, fileCount)
    private val _cacheStats = MutableStateFlow(Pair(0L, 0))
    val cacheStats = _cacheStats.asStateFlow()

    init {
        // Restore previously saved OTG URI (survives app restart)
        val savedUriStr = prefs.getString(PREF_OTG_URI, null)
        if (savedUriStr != null) {
            try {
                _otgDirectoryUri.value = Uri.parse(savedUriStr)
            } catch (e: Exception) {
                prefs.edit().remove(PREF_OTG_URI).apply()
            }
        }
        viewModelScope.launch {
            var previousStatus: DriveStatus? = null
            while (true) {
                val newStatus = withContext(Dispatchers.IO) { computeDriveStatus() }
                if (newStatus != _driveStatus.value) {
                    _driveStatus.value = newStatus
                    // Play a short tone when drive status changes (connected/disconnected)
                    playStatusSound(newStatus)
                }

                // Auto-sync when known drive connects (including on startup)
                if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED &&
                    previousStatus != DriveStatus.KNOWN_DRIVE_CONNECTED &&
                    !_archiveState.value.isArchiving &&
                    !_restoreState.value.isRestoring &&
                    !isSilentSyncing
                ) {
                    silentSyncArchive()
                    refreshCacheStats()
                }

                // Migrate old thumbnails on first run after v2→v3
                if (newStatus == DriveStatus.KNOWN_DRIVE_CONNECTED && previousStatus == null) {
                    withContext(Dispatchers.IO) { previewCache.migrateOldThumbnails() }
                }

                previousStatus = newStatus
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drive status detection
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun computeDriveStatus(): DriveStatus {
        val context = getApplication<Application>()
        val savedUri = _otgDirectoryUri.value ?: return DriveStatus.NO_URI_CONFIGURED

        return try {
            val docFile = DocumentFile.fromTreeUri(context, savedUri)
            if (docFile != null && docFile.exists() && docFile.canRead()) {
                // Drive is accessible — verify UUID matches our archive
                val uuid = archiveUtil.getOrCreateArchiveId(docFile)
                val knownUuid = prefs.getString(PREF_KNOWN_ARCHIVE_ID, null)
                when {
                    uuid == null -> DriveStatus.KNOWN_DRIVE_CONNECTED // can't read UUID, assume OK
                    knownUuid == null -> {
                        // First time: save this UUID as our known archive
                        prefs.edit().putString(PREF_KNOWN_ARCHIVE_ID, uuid).apply()
                        registerArchiveIfNeeded(uuid, savedUri.toString(), docFile)
                        DriveStatus.KNOWN_DRIVE_CONNECTED
                    }
                    uuid == knownUuid -> DriveStatus.KNOWN_DRIVE_CONNECTED
                    else -> {
                        // Different UUID — foreign archive detected
                        _newArchiveId.value = uuid
                        DriveStatus.NEW_ARCHIVE_FOUND
                    }
                }
            } else if (hasAnyUsbDevice(context)) {
                DriveStatus.UNKNOWN_DRIVE_CONNECTED
            } else {
                DriveStatus.KNOWN_DRIVE_DISCONNECTED
            }
        } catch (e: Exception) {
            if (hasAnyUsbDevice(context)) DriveStatus.UNKNOWN_DRIVE_CONNECTED
            else DriveStatus.KNOWN_DRIVE_DISCONNECTED
        }
    }

    private fun registerArchiveIfNeeded(uuid: String, otgUri: String, dir: DocumentFile) {
        val existing = db.archiveDao().getById(uuid)
        if (existing == null) {
            // Use Android ID (unique per device) + short UUID to generate a stable device-based ID
            // This ensures the same device always gets the same archive "number"
            val androidId = android.provider.Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            // Generate a short stable label based on device ID hash
            val shortId = androidId.takeLast(4).uppercase()
            val label = "My1Drive ($shortId)"
            db.archiveDao().insertOrUpdate(
                by.w6.my1drive.data.local.ArchiveEntity(
                    id = uuid,
                    otgUri = otgUri,
                    label = label,
                    firstSeen = System.currentTimeMillis() / 1000
                )
            )
        } else if (existing.otgUri != otgUri) {
            // Update URI if changed (same drive renamed)
            db.archiveDao().insertOrUpdate(existing.copy(otgUri = otgUri))
        }
    }

    private fun hasAnyUsbDevice(context: Context): Boolean {
        // Primary: check extra external directories (most reliable for OTG mass storage)
        return try {
            val externalDirs = context.getExternalFilesDirs(null)
            externalDirs.size > 1 && externalDirs[1] != null
        } catch (e: Exception) {
            // Fallback: UsbManager device list (works for USB accessories/HID)
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
                usbManager.deviceList.isNotEmpty()
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun playStatusSound(status: DriveStatus) {
        try {
            when (status) {
                DriveStatus.KNOWN_DRIVE_CONNECTED, DriveStatus.KNOWN_DRIVE_DISCONNECTED -> {
                    val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
                    generator.startTone(ToneGenerator.TONE_CDMA_PRESSHOLDKEY_LITE, 150)
                    // Release generator after sound plays
                    object : Thread() {
                        override fun run() {
                            try { sleep(200) } catch (_: Exception) {}
                            try { generator.release() } catch (_: Exception) {}
                        }
                    }.start()
                }
                else -> {}
            }
        } catch (_: Exception) {
            // Ignore sound errors
        }
    }

    fun updateOtgStatus() {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { computeDriveStatus() }
            _driveStatus.value = status
            playStatusSound(status)
        }
    }

    /** Accept the newly found archive: update saved UUID and URI, re-sync */
    fun acceptNewArchive() {
        val newId = _newArchiveId.value ?: return
        val uri = _otgDirectoryUri.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            // Check if we have a previous archive with data (DB access on IO thread)
            val oldArchiveId = prefs.getString(PREF_KNOWN_ARCHIVE_ID, null)
            val oldArchiveData = oldArchiveId?.let { db.archiveDao().getById(it) }

            if (oldArchiveData != null && db.mediaDao().getByArchiveId(oldArchiveData.id).isNotEmpty()) {
                // Ask whether to save old archive data or discard (on Main thread)
                withContext(Dispatchers.Main) {
                    _saveOldArchiveRequest.value = oldArchiveData
                    _pendingNewArchiveId.value = newId
                    _pendingNewArchiveUri.value = uri.toString()
                }
                return@launch
            }

            // No old data - just switch
            performSwitchArchive(newId, uri.toString())
        }
    }

    private fun performSwitchArchive(newId: String, uriStr: String) {
        try {
            val uri = Uri.parse(uriStr)
            prefs.edit().putString(PREF_KNOWN_ARCHIVE_ID, newId).apply()
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val dir = DocumentFile.fromTreeUri(getApplication(), uri)
                    if (dir != null) registerArchiveIfNeeded(newId, uriStr, dir)
                } catch (e: Exception) {
                    // Log but continue
                }
            }
            _newArchiveId.value = null
            _pendingNewArchiveId.value = null
            _pendingNewArchiveUri.value = null
            _saveOldArchiveRequest.value = null
            _renameOldArchiveRequest.value = false
            _driveStatus.value = DriveStatus.KNOWN_DRIVE_CONNECTED

            // Use a small delay before silent sync to let the system settle
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                try {
                    silentSyncArchive()
                } catch (e: Exception) {
                    // Ignore sync errors
                }
            }
        } catch (e: Exception) {
            // Fallback: reset drive status to avoid infinite loop
            _driveStatus.value = DriveStatus.KNOWN_DRIVE_DISCONNECTED
        }
    }

    /** Save old archive data with a new label, then switch to new archive */
    fun saveOldArchiveAndSwitch(label: String?) {
        val oldArchive = _saveOldArchiveRequest.value ?: return
        val newId = _pendingNewArchiveId.value ?: return
        val newUri = _pendingNewArchiveUri.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update label if provided
                if (label != null && label.isNotBlank()) {
                    db.archiveDao().updateLabel(oldArchive.id, label)
                }
            } catch (e: Exception) {
                // Ignore DB errors during label update
            }
            withContext(Dispatchers.Main) {
                performSwitchArchive(newId, newUri)
            }
        }
    }

    /** Discard old archive data and switch to new one */
    fun discardOldArchiveAndSwitch() {
        val oldArchiveId = prefs.getString(PREF_KNOWN_ARCHIVE_ID, null)
        val newId = _pendingNewArchiveId.value ?: return
        val newUri = _pendingNewArchiveUri.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (oldArchiveId != null) {
                    db.mediaDao().deleteByArchiveId(oldArchiveId)
                    // Keep archive_info entry but mark it
                }
            } catch (e: Exception) {
                // Ignore DB errors
            }
            withContext(Dispatchers.Main) {
                performSwitchArchive(newId, newUri)
            }
        }
    }

    // State for old archive save/rename dialog
    private val _saveOldArchiveRequest = MutableStateFlow<by.w6.my1drive.data.local.ArchiveEntity?>(null)
    val saveOldArchiveRequest = _saveOldArchiveRequest.asStateFlow()

    private val _pendingNewArchiveId = MutableStateFlow<String?>(null)
    private val _pendingNewArchiveUri = MutableStateFlow<String?>(null)

    private val _renameOldArchiveRequest = MutableStateFlow<Boolean>(false)
    val renameOldArchiveRequest = _renameOldArchiveRequest.asStateFlow()

    /** Called when user chooses to save old archive - triggers rename dialog */
    fun requestRenameOldArchive() {
        _renameOldArchiveRequest.value = true
    }

    fun dismissRenameOldArchive() {
        _renameOldArchiveRequest.value = false
        _saveOldArchiveRequest.value = null
        _pendingNewArchiveId.value = null
        _pendingNewArchiveUri.value = null
    }

    /** Reject the found archive: stay with current archive, ignore this drive */
    fun rejectNewArchive() {
        _newArchiveId.value = null
        _pendingNewArchiveId.value = null
        _pendingNewArchiveUri.value = null
        _saveOldArchiveRequest.value = null
        _renameOldArchiveRequest.value = false
        _driveStatus.value = DriveStatus.KNOWN_DRIVE_DISCONNECTED
    }

    /** Called by OtgThumbnailFetcher when a preview is loaded and cached */
    fun onPreviewCached(hash: String, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.mediaDao().updateLastAccessed(hash, System.currentTimeMillis())
            // Update thumbnailPath in DB if it was null before
            val entity = db.mediaDao().getById(hash)
            if (entity != null && entity.thumbnailPath != path) {
                db.mediaDao().insert(entity.copy(thumbnailPath = path))
            }
            // Evict cache if over limit after loading new thumbnail
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

    /** Public access to PreviewCacheManager for use by UI components */
    fun getPreviewCacheManager(): PreviewCacheManager = previewCache

    fun setCacheMaxMb(mb: Long) {
        previewCache.setMaxBytes(mb * 1024 * 1024)
        viewModelScope.launch { previewCache.evictIfNeeded() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OTG folder management
    // ─────────────────────────────────────────────────────────────────────────

    fun setOtgDirectory(uri: Uri) {
        _otgDirectoryUri.value = uri
        // Persist so it survives app restarts
        prefs.edit().putString(PREF_OTG_URI, uri.toString()).apply()
        viewModelScope.launch {
            _driveStatus.value = computeDriveStatus()
        }
    }

    fun ejectOtg() {
        val context = getApplication<Application>()
        val uri = _otgDirectoryUri.value
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) { /* Ignore */ }
            _otgDirectoryUri.value = null
            prefs.edit().remove(PREF_OTG_URI).apply()
            _driveStatus.value = DriveStatus.NO_URI_CONFIGURED
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Selection
    // ─────────────────────────────────────────────────────────────────────────

    fun toggleSelection(itemId: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(itemId)) remove(itemId) else add(itemId)
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Silent auto-sync
    // ─────────────────────────────────────────────────────────────────────────

    private fun silentSyncArchive() {
        val uri = _otgDirectoryUri.value ?: return
        isSilentSyncing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val dir = DocumentFile.fromTreeUri(context, uri)
                if (dir == null || !dir.exists()) return@launch

                val archiveId = prefs.getString(PREF_KNOWN_ARCHIVE_ID, null)

                val otgFiles = dir.listFiles().filter {
                    !it.isDirectory &&
                    it.name != null &&
                    it.name != ".my1drive_uuid"  // skip marker file
                }

                // ── 1. Detect NEW files on OTG not yet in DB ──────────────────
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
                        // No thumbnail — will be generated on-demand when user views the file
                        val entity = MediaEntity(
                            id = hash,
                            displayName = name,
                            mimeType = mime,
                            size = file.length(),
                            dateModified = file.lastModified() / 1000,
                            otgUri = file.uri.toString(),
                            thumbnailPath = null,
                            duration = null,
                            originalRelativePath = null,
                            archiveId = archiveId
                        )
                        db.mediaDao().insert(entity)
                        addedCount++
                    } else if (existing.archiveId == null && archiveId != null) {
                        // Back-fill archiveId for existing entries
                        db.mediaDao().insert(existing.copy(archiveId = archiveId))
                    }
                }


                // ── 2. Detect MISSING files (in DB but no longer on OTG) ──────
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
                    // Only show notification once per unique set of missing files
                    val missingHash = missingNames.sorted().joinToString(",")
                    val wasDismissed = prefs.getBoolean(PREF_MISSING_FILES_DISMISSED, false)
                    val lastHash = prefs.getString(PREF_MISSING_FILES_HASH, null)
                    if (!wasDismissed || lastHash != missingHash) {
                        _missingFilesNotification.value = missingNames
                    }
                }
            } catch (e: Exception) {
                // Silent — auto-sync errors are swallowed
            } finally {
                isSilentSyncing = false
            }
        }
    }

    fun dismissMissingFilesNotification() {
        val names = _missingFilesNotification.value
        if (names != null) {
            // Save a hash of the missing files list so we don't show it again
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

    // ─────────────────────────────────────────────────────────────────────────
    // Manual sync (with visible log)
    // ─────────────────────────────────────────────────────────────────────────

    fun syncArchive() {
        val uri = _otgDirectoryUri.value ?: return
        viewModelScope.launch {
            _syncState.value = "Синхронизация: чтение папки OTG..."
            try {
                val context = getApplication<Application>()
                val dir = DocumentFile.fromTreeUri(context, uri)
                if (dir == null || !dir.exists()) {
                    throw Exception("Нет доступа к OTG накопителю")
                }

                val files = dir.listFiles()
                val total = files.size
                if (total == 0) {
                    _syncState.value = "Синхронизация завершена: папка пуста."
                    return@launch
                }

                var syncedCount = 0
                var skippedCount = 0
                val logSb = StringBuilder()
                logSb.appendLine("=== Sync Archive Log ===")
                logSb.appendLine("Total files found: $total")

                withContext(Dispatchers.IO) {
                    for ((index, file) in files.withIndex()) {
                        if (file.isDirectory) {
                            logSb.appendLine("Skipping directory: ${file.name}")
                            continue
                        }
                        val name = file.name ?: continue
                        val mime = file.type ?: "image/jpeg"

                        _syncState.value = "Сканирование: $name (${index + 1} из $total)"

                        val hash = try {
                            archiveUtil.calculateSha256(file.uri)
                        } catch (e: Exception) {
                            logSb.appendLine("Failed to read/hash ${file.name}: ${e.javaClass.simpleName}: ${e.message}")
                            skippedCount++
                            continue
                        }

                        val existing = db.mediaDao().getById(hash)
                        if (existing == null) {
                            val thumbFile = archiveUtil.createThumbnail(file.uri, mime.startsWith("video"))
                            val entity = MediaEntity(
                                id = hash,
                                displayName = name,
                                mimeType = mime,
                                size = file.length(),
                                dateModified = file.lastModified() / 1000,
                                otgUri = file.uri.toString(),
                                thumbnailPath = thumbFile?.absolutePath,
                                duration = null,
                                originalRelativePath = null
                            )
                            db.mediaDao().insert(entity)
                            syncedCount++
                            logSb.appendLine("Imported new file: $name (hash=$hash, size=${file.length()} bytes)")
                        } else {
                            logSb.appendLine("Already exists in database: $name (hash=$hash)")
                        }
                    }
                }

                logSb.appendLine("Sync completed successfully: imported $syncedCount, skipped $skippedCount")
                _syncState.value = logSb.toString()
                repository.refresh()
            } catch (e: Exception) {
                _syncState.value = "Ошибка синхронизации: ${e.localizedMessage}"
            }
        }
    }

    fun dismissSync() {
        _syncState.value = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Archive (copy to OTG)
    // ─────────────────────────────────────────────────────────────────────────

    // State for showing folder picker during archiving (tree mode)
    private val _showArchiveFolderPicker = MutableStateFlow<ArchiveFolderPickerState?>(null)
    val showArchiveFolderPicker = _showArchiveFolderPicker.asStateFlow()

    data class ArchiveFolderPickerState(
        val existingFolders: List<String>,
        val selectedItems: List<MediaItem>
    )

    /**
     * Start archiving - if folder tree mode is enabled and we have items with different paths,
     * show folder picker first. Otherwise, archive directly to target URI.
     */
    fun startArchiving(targetUri: Uri) {
        val selectedItems = mediaItems.value.filter { it.id in _selectedIds.value }
        if (selectedItems.isEmpty()) return

        val useTree = _useFolderTree.value
        if (useTree) {
            // Show folder picker dialog to let user choose where to save
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val dir = DocumentFile.fromTreeUri(getApplication(), targetUri)
                    val folders = dir?.listFiles()?.filter { it.isDirectory }?.map { it.name ?: "?" } ?: emptyList()
                    _showArchiveFolderPicker.value = ArchiveFolderPickerState(
                        existingFolders = folders,
                        selectedItems = selectedItems
                    )
                } catch (e: Exception) {
                    // Fallback: just archive to root
                    performArchiving(selectedItems, targetUri, null)
                }
            }
        } else {
            performArchiving(selectedItems, targetUri, null)
        }
    }

    fun dismissArchiveFolderPicker() {
        _showArchiveFolderPicker.value = null
    }

    /** Archive to a specific subfolder (or null for root) */
    fun archiveToSubfolder(subfolderName: String?) {
        val state = _showArchiveFolderPicker.value ?: return
        val targetUri = _otgDirectoryUri.value ?: return
        _showArchiveFolderPicker.value = null
        performArchiving(state.selectedItems, targetUri, subfolderName)
    }

    private fun performArchiving(items: List<MediaItem>, targetUri: Uri, subfolderName: String?) {
        if (items.isEmpty()) return

        viewModelScope.launch {
            val targetSubfolderUri = if (subfolderName != null) {
                val dir = DocumentFile.fromTreeUri(getApplication(), targetUri)
                val subDir = dir?.findFile(subfolderName) ?: dir?.createDirectory(subfolderName)
                subDir?.uri ?: targetUri
            } else {
                targetUri
            }
            val selectedItems = items
            _archiveState.value = ArchiveState(isArchiving = true, totalFiles = selectedItems.size)
            _archiveState.value = ArchiveState(isArchiving = true, totalFiles = selectedItems.size)

            val successfullyCopied = mutableListOf<ArchivedInfo>()
            val skippedItems = mutableListOf<Pair<MediaItem, String>>()
            var errorMsg: String? = null

            for ((index, item) in selectedItems.withIndex()) {
                _archiveState.value = _archiveState.value.copy(
                    currentFileName = item.displayName,
                    currentFileIndex = index + 1,
                    currentStep = ""
                )

                var successInfo: ArchivedInfo? = null
                var itemError: String? = null
                var isSkipped = false
                var skippedReason = ""

                archiveUtil.copyAndVerifyItem(item, targetUri).collect { result ->
                    when (result) {
                        is CopyVerifyResult.Progress -> {
                            val overallProgress = (index.toFloat() + result.progressFraction) / selectedItems.size
                            _archiveState.value = _archiveState.value.copy(
                                currentStep = result.step,
                                progressFraction = overallProgress
                            )
                        }
                        is CopyVerifyResult.Success -> {
                            successInfo = ArchivedInfo(result.item, result.hash, result.otgUri, result.thumbnailPath)
                        }
                        is CopyVerifyResult.Skipped -> {
                            isSkipped = true; skippedReason = result.message
                        }
                        is CopyVerifyResult.Error -> {
                            itemError = result.message
                        }
                    }
                }

                if (successInfo != null) successfullyCopied.add(successInfo!!)
                else if (isSkipped) skippedItems.add(item to skippedReason)
                else if (itemError != null) errorMsg = itemError
            }

            if (skippedItems.isNotEmpty() || errorMsg != null) {
                lastArchiveSummary = buildString {
                    append("Резервное копирование завершено.\n")
                    append("Успешно скопировано: ${successfullyCopied.size} из ${selectedItems.size} файлов.\n")
                    append("Пропущено файлов: ${skippedItems.size}\n\n")
                    if (skippedItems.isNotEmpty()) {
                        append("Причина: Файлы сохранены в облаке или защищены системой.\n")
                        append("Список пропущенных файлов:\n")
                        skippedItems.forEach { (item, reason) -> append("- ${item.displayName}: $reason\n") }
                    }
                    if (errorMsg != null) append("\nКритическая ошибка:\n$errorMsg")
                }
            } else {
                lastArchiveSummary = null
            }

            if (successfullyCopied.isNotEmpty()) {
                processDeletions(successfullyCopied)
            } else {
                _archiveState.value = ArchiveState(
                    isArchiving = false,
                    error = lastArchiveSummary ?: "Нет доступных файлов для копирования"
                )
                lastArchiveSummary = null
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
            _archiveState.value = ArchiveState(isArchiving = false, error = lastArchiveSummary)
            lastArchiveSummary = null
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
                    _archiveState.value = ArchiveState(isArchiving = false, error = lastArchiveSummary)
                    lastArchiveSummary = null
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
        _archiveState.value = ArchiveState(isArchiving = false, error = lastArchiveSummary)
        lastArchiveSummary = null
    }

    fun dismissError() {
        _archiveState.value = _archiveState.value.copy(error = null)
    }

    fun refresh() {
        repository.refresh()
    }

    /**
     * Deletes the archived record from the database.
     * Does NOT delete the original from OTG drive (files on OTG are only deleted via restore/unarchive).
     * Does NOT delete the cached preview (previews cannot be deleted separately).
     */
    /** Delete all selected items (works for both archived and local) */
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

    // ─────────────────────────────────────────────────────────────────────────
    // Restore
    // ─────────────────────────────────────────────────────────────────────────

    fun setAskRestorePath(value: Boolean) {
        _askRestorePath.value = value
        prefs.edit().putBoolean(PREF_ASK_RESTORE_PATH, value).apply()
    }

    // �"?�"? Create folder on OTG �"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�"?�
    /** Create a new folder on the OTG drive */
    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog = _showCreateFolderDialog.asStateFlow()

    fun requestCreateFolder() {
        _showCreateFolderDialog.value = true
    }

    fun dismissCreateFolderDialog() {
        _showCreateFolderDialog.value = false
    }

    fun createFolderOnOtg(folderName: String) {
        val otgUri = _otgDirectoryUri.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = DocumentFile.fromTreeUri(getApplication(), otgUri)
                dir?.createDirectory(folderName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _showCreateFolderDialog.value = false
    }

    fun setUseFolderTree(value: Boolean) {
        _useFolderTree.value = value
        prefs.edit().putBoolean(PREF_USE_FOLDER_TREE, value).apply()
    }

    fun requestRestore() {
        val selectedItems = mediaItems.value.filter { it.id in _selectedIds.value && it.status == MediaStatus.ARCHIVED_OTG }
        if (selectedItems.isEmpty()) return

        val askPath = _askRestorePath.value
        val allHavePath = selectedItems.all { it.originalRelativePath != null }
        val commonPath = if (allHavePath && selectedItems.isNotEmpty()) selectedItems.first().originalRelativePath else null

        when {
            !askPath && allHavePath -> startRestoring(selectedItems, targetDirUri = null)
            !askPath && !allHavePath -> { pendingRestoreItems = selectedItems; _restoreRequest.value = RestoreRequest.NeedFolderPicker }
            askPath && allHavePath && commonPath != null -> { pendingRestoreItems = selectedItems; _restoreRequest.value = RestoreRequest.AskOriginalOrCustom(selectedItems, commonPath) }
            else -> { pendingRestoreItems = selectedItems; _restoreRequest.value = RestoreRequest.NeedFolderPicker }
        }
    }

    fun restoreToOriginalPath() {
        val items = pendingRestoreItems.toList()
        pendingRestoreItems = emptyList()
        _restoreRequest.value = null
        startRestoring(items, targetDirUri = null)
    }

    fun restoreToChosenFolder(folderUri: Uri) {
        val items = pendingRestoreItems.toList()
        pendingRestoreItems = emptyList()
        _restoreRequest.value = null
        startRestoring(items, targetDirUri = folderUri)
    }

    fun dismissRestoreRequest() {
        pendingRestoreItems = emptyList()
        _restoreRequest.value = null
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
                        is RestoreResult.Error -> errors.add("${result.displayName}: ${result.message}")
                    }
                }
            }

            _selectedIds.value = emptySet()
            repository.refresh()

            val errorSummary = when {
                errors.isNotEmpty() -> "Восстановлено: $successCount из ${items.size}.\n\nОшибки:\n" + errors.joinToString("\n")
                successCount < items.size -> "Восстановлено: $successCount из ${items.size}."
                else -> null
            }
            _restoreState.value = RestoreState(isRestoring = false, successCount = successCount, error = errorSummary)
        }
    }

    fun dismissRestoreError() {
        _restoreState.value = _restoreState.value.copy(error = null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Format OTG
    // ─────────────────────────────────────────────────────────────────────────

    fun dismissFormat() {
        _formatState.value = null
    }

    fun formatOtg() {
        val uri = _otgDirectoryUri.value ?: return
        viewModelScope.launch {
            _formatState.value = FormatState.Progress("Форматирование: подготовка к очистке...")
            try {
                val context = getApplication<Application>()
                val dir = DocumentFile.fromTreeUri(context, uri)
                if (dir == null || !dir.exists() || !dir.canWrite()) throw Exception("Нет доступа к OTG накопителю для записи")

                val files = dir.listFiles()
                if (files.isEmpty()) {
                    repository.clearAllArchivedItems()
                    _formatState.value = FormatState.Success("Накопитель очищен! База данных архива сброшена.")
                    repository.refresh()
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    for ((index, file) in files.withIndex()) {
                        _formatState.value = FormatState.Progress("Удаление: ${file.name} (${index + 1} из ${files.size})")
                        deleteRecursively(file)
                    }
                    repository.clearAllArchivedItems()
                }
                _formatState.value = FormatState.Success("Форматирование успешно завершено!")
                repository.refresh()
            } catch (e: Exception) {
                _formatState.value = FormatState.Error("Ошибка форматирования: ${e.localizedMessage}")
            }
        }
    }

    private fun deleteRecursively(file: DocumentFile) {
        if (file.isDirectory) file.listFiles().forEach { deleteRecursively(it) }
        file.delete()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Diagnostics
    // ─────────────────────────────────────────────────────────────────────────

    private val _diagnosticReport = MutableStateFlow<String?>(null)
    val diagnosticReport = _diagnosticReport.asStateFlow()

    fun runDiagnostic() {
        viewModelScope.launch {
            _diagnosticReport.value = "Выполняется диагностика..."
            try {
                val report = by.w6.my1drive.utils.DiagnosticUtil.runFullDiagnostic(getApplication(), _otgDirectoryUri.value)
                _diagnosticReport.value = report
            } catch (e: Exception) {
                _diagnosticReport.value = "Diagnostic failed: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    fun dismissDiagnostic() {
        _diagnosticReport.value = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

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
