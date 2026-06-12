package by.w6.my1drive.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.data.local.ArchiveEntity
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.utils.PreviewCacheManager
import coil.ImageLoader
import java.io.File

@Composable
fun UnknownDriveBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.drive_unknown_connected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
fun PartialAccessBanner(onGrantFullAccess: () -> Unit, onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.partial_access_banner_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.partial_access_banner_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrantFullAccess, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.btn_grant_full_access), maxLines = 1) }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.btn_open_settings), maxLines = 1) }
            }
        }
    }
}

@Composable
fun PhotosRoute(
    viewModel: GalleryViewModel, selectedIds: Set<String>, imageLoader: ImageLoader,
    isOtgConnected: Boolean, onItemClick: (MediaItem) -> Unit, onItemLongClick: (MediaItem) -> Unit
) {
    val groupedItems by viewModel.groupedMediaItems.collectAsState()
    PhotosGridTab(groupedItems = groupedItems, selectedIds = selectedIds, imageLoader = imageLoader,
        isOtgConnected = isOtgConnected, onItemClick = onItemClick, onItemLongClick = onItemLongClick)
}

@Composable
fun ArchiveRoute(
    viewModel: GalleryViewModel, selectedIds: Set<String>, imageLoader: ImageLoader,
    isOtgConnected: Boolean, onItemClick: (MediaItem) -> Unit, onItemLongClick: (MediaItem) -> Unit
) {
    val archivedGroupedItems by viewModel.archivedGroupedItems.collectAsState()
    val archiveFolderPath by viewModel.currentArchiveFolderPath.collectAsState()
    val archiveFolders by viewModel.archiveSubfolders.collectAsState()
    Column {
        PhotosGridTab(groupedItems = archivedGroupedItems, selectedIds = selectedIds, imageLoader = imageLoader,
            isOtgConnected = isOtgConnected, onItemClick = onItemClick, onItemLongClick = onItemLongClick,
            onFolderClick = { folderName -> viewModel.navigateToArchiveFolder(folderName) })
        if (archiveFolderPath != null || archiveFolders.isNotEmpty()) {
            ArchiveBreadcrumbBar(currentPath = archiveFolderPath, onNavigateUp = { viewModel.navigateUpArchiveFolder() }, onNavigateRoot = { viewModel.navigateToArchiveRoot() })
        }
    }
}

@Composable
fun NewArchiveFoundDialog(newArchiveId: String?, onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = onReject,
        icon = { Icon(Icons.Default.SdStorage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = { Text(stringResource(R.string.new_archive_found_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.new_archive_found_msg))
                if (newArchiveId != null) Text("ID: ${newArchiveId.take(8)}...", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text(stringResource(R.string.btn_connect_to_archive)) } },
        dismissButton = { TextButton(onClick = onReject) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun SaveOldArchiveDialog(oldArchive: ArchiveEntity, onSave: (String) -> Unit, onDiscard: () -> Unit) {
    var archiveLabel by remember { mutableStateOf(oldArchive.label) }
    AlertDialog(
        onDismissRequest = onDiscard,
        icon = { Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = { Text("Old archive found", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("A different archive was detected. What should we do with the old archive data (${oldArchive.label})?")
                Spacer(Modifier.height(12.dp))
                Text("Save old archive as:", fontWeight = FontWeight.Medium)
                OutlinedTextField(value = archiveLabel, onValueChange = { archiveLabel = it }, label = { Text("Archive name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("If saved, old archive data remains accessible in the app. If discarded, all records will be removed.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        confirmButton = { Button(onClick = { onSave(archiveLabel) }) { Text("Save and switch") } },
        dismissButton = { TextButton(onClick = onDiscard) { Text("Discard") } }
    )
}

@Composable
fun MissingFilesDialog(missingNames: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_sync_missing_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.auto_sync_missing_msg, missingNames.joinToString("\n"))) },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) } }
    )
}

@Composable
fun GalleryScreenContent(
    modifier: Modifier,
    selectedIds: Set<String>,
    driveStatus: DriveStatus,
    otgDirectoryUri: Uri?,
    isOtgConnected: Boolean,
    hasPartialAccess: Boolean,
    currentScreenRoute: String,
    newArchiveId: String?,
    missingFilesNotification: List<String>?,
    autoSyncAddedCount: Int,
    activePreviewItem: MediaItem?,
    showInfoDialogItem: MediaItem?,
    showOtgGuideDialog: Boolean,
    imageLoader: ImageLoader,
    viewModel: GalleryViewModel,
    onSelectOtgDirectory: () -> Unit,
    onRequestFullAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onSetActivePreview: (MediaItem?) -> Unit,
    onSetShowInfoDialog: (MediaItem?) -> Unit,
    onSetShowOtgGuide: (Boolean) -> Unit,
    previewCacheManager: PreviewCacheManager
) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GooglePhotosTopBar(selectedCount = selectedIds.size, driveStatus = driveStatus, otgUriSet = otgDirectoryUri != null, onClearSelection = onClearSelection, onSelectOtgClick = onSelectOtgDirectory)

            if (driveStatus == DriveStatus.UNKNOWN_DRIVE_CONNECTED) UnknownDriveBanner()
            if (hasPartialAccess) PartialAccessBanner(onGrantFullAccess = onRequestFullAccess, onOpenSettings = onOpenSettings)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentScreenRoute) {
                    "photos" -> PhotosRoute(viewModel = viewModel, selectedIds = selectedIds, imageLoader = imageLoader, isOtgConnected = isOtgConnected,
                        onItemClick = { item -> if (selectedIds.isNotEmpty()) viewModel.toggleSelection(item.id) else onSetShowInfoDialog(item) },
                        onItemLongClick = { item -> viewModel.toggleSelection(item.id) })
                    "archive" -> ArchiveRoute(viewModel = viewModel, selectedIds = selectedIds, imageLoader = imageLoader, isOtgConnected = isOtgConnected,
                        onItemClick = { item -> if (selectedIds.isNotEmpty()) viewModel.toggleSelection(item.id) else onSetShowInfoDialog(item) },
                        onItemLongClick = { item -> viewModel.toggleSelection(item.id) })
                    "settings" -> SettingsTab(previewCacheManager = previewCacheManager, onClearCache = { viewModel.clearPreviewCache() })
                }
            }
        }

        showInfoDialogItem?.let { item ->
            InfoDialog(item = item, imageLoader = imageLoader ?: return@let, isOtgConnected = isOtgConnected,
                onOpenFullscreen = { onSetActivePreview(item) }, onDeleteFile = { viewModel.deleteArchivedRecord(item) }, onDismiss = { onSetShowInfoDialog(null) })
        }

        if (showOtgGuideDialog) {
            OtgGuideDialog(onConfirm = { onSetShowOtgGuide(false); onSelectOtgDirectory() }, onDismiss = { onSetShowOtgGuide(false) })
        }

        if (driveStatus == DriveStatus.NEW_ARCHIVE_FOUND) {
            NewArchiveFoundDialog(newArchiveId = newArchiveId, onAccept = { viewModel.acceptNewArchive() }, onReject = { viewModel.rejectNewArchive() })
        }

        val saveOldArchiveRequest by viewModel.saveOldArchiveRequest.collectAsState()
        saveOldArchiveRequest?.let { oldArchive ->
            SaveOldArchiveDialog(oldArchive = oldArchive, onSave = { label -> viewModel.saveOldArchiveAndSwitch(label) }, onDiscard = { viewModel.discardOldArchiveAndSwitch() })
        }

        missingFilesNotification?.let { missingNames ->
            MissingFilesDialog(missingNames = missingNames, onDismiss = { viewModel.dismissMissingFilesNotification() })
        }

        activePreviewItem?.let { item ->
            FullscreenPreview(item = item, imageLoader = imageLoader ?: return@let, onClose = { onSetActivePreview(null) }, onShowInfo = { onSetShowInfoDialog(item) })
        }
    }
}
