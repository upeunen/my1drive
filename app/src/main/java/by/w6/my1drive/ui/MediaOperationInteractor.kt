package by.w6.my1drive.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class MediaOperationInteractor(
    private val application: Application,
    private val repository: MediaRepository,
    private val scope: CoroutineScope,
    private val otgManager: OtgConnectionManager,
    private val archiveInteractor: ArchiveInteractor,
    private val isOtgConnected: StateFlow<Boolean>,
    private val onArchiveTaskReady: (List<MediaItem>, Uri) -> Unit = { _, _ -> }
) {
    // ─── Delete State ───
    private val _deviceDeleteSender = MutableStateFlow<IntentSender?>(null)
    val deviceDeleteSender = _deviceDeleteSender.asStateFlow()

    private val _deviceDeletePendingItems = mutableListOf<MediaItem>()
    
    private val _folderPermissionRequest = MutableStateFlow<Intent?>(null)
    val folderPermissionRequest = _folderPermissionRequest.asStateFlow()

    private val _pendingDelete = MutableStateFlow<List<MediaItem>?>(null)
    val pendingDelete: StateFlow<List<MediaItem>?> = _pendingDelete.asStateFlow()

    fun requestDelete(items: List<MediaItem>) {
        if (items.isNotEmpty()) {
            _pendingDelete.value = items
        }
    }

    fun dismissDelete() {
        _pendingDelete.value = null
    }

    val missingFoldersQueue = mutableListOf<String>()
    var pendingDeleteTask: List<MediaItem>? = null
    var pendingArchiveTask: Pair<List<MediaItem>, Uri>? = null

    // ─── Share State ───
    private val _isSharingPreparing = MutableStateFlow(false)
    val isSharingPreparing = _isSharingPreparing.asStateFlow()

    // ─── Folder Creation State ───
    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog = _showCreateFolderDialog.asStateFlow()


    // ─── Share Operations ───

    fun shareMediaItem(item: MediaItem, context: Context, onError: (String) -> Unit) {
        if (item.status == MediaStatus.ON_DEVICE) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, item.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(by.w6.my1drive.R.string.btn_share)))
            } catch (e: Exception) {
                onError(e.localizedMessage ?: context.getString(by.w6.my1drive.R.string.error_share_failed))
            }
        } else if (item.status == MediaStatus.ARCHIVED_OTG) {
            val activeUuid = otgManager.activeArchiveUuid.value
            val isCurrentConnected = isOtgConnected.value && item.archiveUuid == activeUuid
            
            _isSharingPreparing.value = true
            scope.launch(Dispatchers.IO) {
                try {
                    val sharedTempDir = File(context.cacheDir, "shared_temp").also { it.mkdirs() }
                    val tempFile = File(sharedTempDir, item.displayName)
                    
                    if (isCurrentConnected && !item.otgUri.isNullOrEmpty()) {
                        val otgUri = Uri.parse(item.otgUri)
                        val inputStream = context.contentResolver.openInputStream(otgUri)
                            ?: throw Exception(context.getString(by.w6.my1drive.R.string.error_open_file_failed))
                        inputStream.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    } else {
                        val thumbPath = item.thumbnailPath
                        if (thumbPath.isNullOrEmpty()) {
                            throw Exception(context.getString(by.w6.my1drive.R.string.error_otg_offline_no_thumbnail))
                        }
                        val thumbFile = File(thumbPath)
                        if (!thumbFile.exists()) {
                            throw Exception(context.getString(by.w6.my1drive.R.string.error_thumbnail_not_found_local))
                        }
                        thumbFile.inputStream().use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
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
                    val chooserTitle = if (isCurrentConnected) context.getString(by.w6.my1drive.R.string.btn_share) else context.getString(by.w6.my1drive.R.string.share_thumbnail)
                    context.startActivity(Intent.createChooser(intent, chooserTitle))
                } catch (e: Exception) {
                    _isSharingPreparing.value = false
                    scope.launch(Dispatchers.Main) {
                        onError(e.localizedMessage ?: context.getString(by.w6.my1drive.R.string.error_share_failed))
                    }
                }
            }
        }
    }

    fun shareSelectedItems(selected: List<MediaItem>, context: Context, onError: (String) -> Unit) {
        if (selected.isEmpty()) return

        val onDeviceItems = selected.filter { it.status == MediaStatus.ON_DEVICE }
        val archivedItems  = selected.filter { it.status == MediaStatus.ARCHIVED_OTG }

        if (archivedItems.isEmpty()) {
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
                onError(e.localizedMessage ?: context.getString(by.w6.my1drive.R.string.error_share_failed))
            }
        } else {
            _isSharingPreparing.value = true
            scope.launch(Dispatchers.IO) {
                try {
                    val sharedTempDir = File(context.cacheDir, "shared_temp").also { it.mkdirs() }
                    val activeUuid = otgManager.activeArchiveUuid.value
                    val isOtgConnectedVal = isOtgConnected.value

                    // Async copy all archived items
                    val deferredUris = archivedItems.map { item ->
                        async {
                            val isCurrentConnected = isOtgConnectedVal && item.archiveUuid == activeUuid
                            val tempFile = File(sharedTempDir, item.displayName)
                            var copied = false
                            
                            if (isCurrentConnected && !item.otgUri.isNullOrEmpty()) {
                                try {
                                    val otgUri = Uri.parse(item.otgUri)
                                    context.contentResolver.openInputStream(otgUri)?.use { input ->
                                        tempFile.outputStream().use { output -> input.copyTo(output) }
                                        copied = true
                                    }
                                } catch (_: Exception) {}
                            }
                            
                            if (!copied) {
                                val thumbPath = item.thumbnailPath
                                if (!thumbPath.isNullOrEmpty()) {
                                    val thumbFile = File(thumbPath)
                                    if (thumbFile.exists()) {
                                        thumbFile.inputStream().use { input ->
                                            tempFile.outputStream().use { output -> input.copyTo(output) }
                                            copied = true
                                        }
                                    }
                                }
                            }
                            
                            if (!copied) {
                                throw Exception(context.getString(by.w6.my1drive.R.string.error_read_offline_file, item.displayName))
                            }
                            
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                        }
                    }

                    val uris = ArrayList<Uri>()
                    onDeviceItems.forEach { uris.add(it.uri) }
                    
                    val archivedUris = deferredUris.awaitAll()
                    uris.addAll(archivedUris)

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
                    scope.launch(Dispatchers.Main) {
                        onError(e.localizedMessage ?: context.getString(by.w6.my1drive.R.string.error_share_files_failed))
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

    // ─── Delete Operations ───

    fun startDeletingWithPermissionCheck(items: List<MediaItem>) {
        val deviceItems = items.filter { it.status == MediaStatus.ON_DEVICE }
        val archivedItems = items.filter { it.status == MediaStatus.ARCHIVED_OTG }
        
        val uniqueFolders = deviceItems.map { getFolderToRequest(it.originalRelativePath) }
            .filter { it.isNotEmpty() }
            .toSet()
            
        val missingFolders = uniqueFolders.filter { !hasPermissionForFolder(application, it) }

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

    fun confirmDelete() {
        val items = _pendingDelete.value ?: return
        _pendingDelete.value = null
        startDeletingWithPermissionCheck(items)
    }

    fun onDeviceDeleteResult(success: Boolean) {
        if (success) {
            directDeleteDeviceItems(_deviceDeletePendingItems)
        } else {
            _deviceDeletePendingItems.clear()
        }
        _deviceDeleteSender.value = null
    }

    private fun deleteDeviceItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val remainingItems = mutableListOf<MediaItem>()
        var anyDeleted = false
        for (item in items) {
            val treeUri = findMatchingTreeUriForFile(application, item.originalRelativePath)
            val doc = if (treeUri != null) {
                findFileInTree(application, treeUri, item.originalRelativePath, item.displayName)
            } else null
            
            if (doc != null && doc.exists() && doc.delete()) {
                anyDeleted = true
                val externalDir = android.os.Environment.getExternalStorageDirectory()
                val relPath = item.originalRelativePath?.trim('/', '\\') ?: ""
                val fileOnDisk = if (relPath.isNotEmpty()) {
                    java.io.File(externalDir, "$relPath/${item.displayName}")
                } else {
                    java.io.File(externalDir, item.displayName)
                }
                android.media.MediaScannerConnection.scanFile(
                    application,
                    arrayOf(fileOnDisk.absolutePath),
                    arrayOf(item.mimeType)
                ) { _, _ ->
                    scope.launch { repository.refresh() }
                }
            } else {
                remainingItems.add(item)
            }
        }
        if (anyDeleted && remainingItems.isEmpty()) return
        if (remainingItems.isNotEmpty()) fallbackDeleteDeviceItems(remainingItems)
    }

    private fun fallbackDeleteDeviceItems(items: List<MediaItem>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(application.contentResolver, items.map { it.uri })
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
        val remaining = items.toMutableList()
        for (item in items) {
            try {
                application.contentResolver.delete(item.uri, null, null)
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
        scope.launch { repository.refresh() }
    }

    // ─── Folder Permissions (Android 11+) ───

    fun getFolderToRequest(relativePath: String?): String {
        val cleanPath = relativePath?.trim('/', '\\') ?: return ""
        val segment = cleanPath.substringBefore('/', "")
        if (segment.isNotEmpty()) return segment
        return ""
    }

    fun hasPermissionForFolder(context: Context, folderName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        if (folderName.isEmpty()) return true
        val uriPermissions = context.contentResolver.persistedUriPermissions
        for (perm in uriPermissions) {
            if (perm.isWritePermission) {
                try {
                    val treeDocId = DocumentsContract.getTreeDocumentId(perm.uri)
                    val parts = treeDocId.split(":")
                    if (parts.size >= 2) {
                        val volume = parts[0]
                        val path = parts[1].trim('/', '\\')
                        if (volume.equals("primary", ignoreCase = true)) {
                            if (path.isEmpty()) return true
                            val cleanFolder = folderName.trim('/', '\\')
                            if (cleanFolder.equals(path, ignoreCase = true) || cleanFolder.startsWith("$path/", ignoreCase = true) || cleanFolder.startsWith("$path\\", ignoreCase = true)) {
                                return true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return false
    }

    fun requestNextFolderPermission() {
        if (missingFoldersQueue.isEmpty()) {
            val task = pendingDeleteTask
            pendingDeleteTask = null
            if (task != null) {
                val deviceItems = task.filter { it.status == MediaStatus.ON_DEVICE }
                val archivedItems = task.filter { it.status == MediaStatus.ARCHIVED_OTG }
                deleteDeviceItems(deviceItems)
                archiveInteractor.deleteArchivedItems(archivedItems)
            }
            val archTask = pendingArchiveTask
            pendingArchiveTask = null
            if (archTask != null) {
                onArchiveTaskReady(archTask.first, archTask.second)
            }
            return
        }
        val nextFolder = missingFoldersQueue.first()
        val clean = nextFolder.trim('/', '\\').replace('\\', '/')
        val docId = if (clean.isNotEmpty()) "primary:$clean" else "primary:"
        val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")
        val initialUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
        _folderPermissionRequest.value = intent
    }

    fun onFolderPermissionResult(success: Boolean, uri: Uri?) {
        if (success && uri != null) {
            try {
                application.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        if (missingFoldersQueue.isNotEmpty()) {
            missingFoldersQueue.removeAt(0)
        }
        requestNextFolderPermission()
    }

    fun clearFolderPermissionRequest() {
        _folderPermissionRequest.value = null
    }

    private fun findMatchingTreeUriForFile(context: Context, relativePath: String?): Uri? {
        val cleanPath = relativePath?.trim('/', '\\') ?: ""
        val permissions = context.contentResolver.persistedUriPermissions
        for (perm in permissions) {
            if (perm.isWritePermission) {
                try {
                    val treeDocId = DocumentsContract.getTreeDocumentId(perm.uri)
                    val parts = treeDocId.split(":")
                    if (parts.size >= 2) {
                        val volume = parts[0]
                        val path = parts[1].trim('/', '\\')
                        if (volume.equals("primary", ignoreCase = true)) {
                            if (path.isEmpty()) return perm.uri
                            if (cleanPath.equals(path, ignoreCase = true) || cleanPath.startsWith("$path/", ignoreCase = true) || cleanPath.startsWith("$path\\", ignoreCase = true)) {
                                return perm.uri
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun findFileInTree(context: Context, treeUri: Uri, relativePath: String?, fileName: String): DocumentFile? {
        val cleanPath = relativePath?.trim('/', '\\') ?: ""
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val fullRelativePath = if (cleanPath.isNotEmpty()) "$cleanPath/$fileName" else fileName
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
        return null
    }

    // ─── Create Folder ───

    fun requestCreateFolder() { _showCreateFolderDialog.value = true }
    fun dismissCreateFolderDialog() { _showCreateFolderDialog.value = false }

    fun createFolderOnOtg(folderName: String) {
        otgManager.otgDirectoryUri.value?.let { uri ->
            scope.launch(Dispatchers.IO) { 
                try { 
                    DocumentFile.fromTreeUri(application, uri)?.createDirectory(folderName) 
                } catch (e: Exception) { 
                    by.w6.my1drive.utils.DebugLogBuffer.log("MediaOperationInteractor", "Create folder error: ${e.message}") 
                } 
            }
        }
        _showCreateFolderDialog.value = false
    }
}
