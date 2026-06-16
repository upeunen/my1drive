package by.w6.my1drive.ui

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.ui.GalleryItem
import by.w6.my1drive.utils.PreviewCacheManager
import coil.ImageLoader

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
    PhotosGridTab(groupedItems = archivedGroupedItems, selectedIds = selectedIds, imageLoader = imageLoader,
        isOtgConnected = isOtgConnected, onItemClick = onItemClick, onItemLongClick = onItemLongClick)
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
fun UnknownDriveDialog(
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Неизвестный носитель", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text("Подключен неизвестный носитель. Создать новый архив, или если вернётся старый — сможете его синхронизировать")
        },
        confirmButton = {
            Button(onClick = onCreateNew) {
                Text("Создать новый")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    pendingDelete: List<MediaItem>,
    isArchiveTab: Boolean,
    isOtgConnected: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (pendingDelete.size == 1) stringResource(R.string.delete_confirm_title) else "${stringResource(R.string.delete_confirm_title)} (${pendingDelete.size})",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (pendingDelete.size == 1) {
                    Text(stringResource(R.string.delete_confirm_msg, pendingDelete.first().displayName))
                } else {
                    Text(stringResource(R.string.action_delete_files_question, pendingDelete.size))
                }
                if (isArchiveTab) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.delete_archived_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (pendingDelete.size == 1) stringResource(R.string.btn_delete_otg) else stringResource(R.string.action_delete_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
fun GalleryScreenContent(
    modifier: Modifier,
    selectedIds: Set<String>,
    otgDirectoryUri: Uri?,
    isOtgConnected: Boolean,
    hasPartialAccess: Boolean,
    currentScreenRoute: String,
    missingFilesNotification: List<String>?,
    autoSyncAddedCount: Int,
    activePreviewState: FullscreenState?,
    showInfoDialogItem: MediaItem?,
    showOtgGuideDialog: Boolean,
    imageLoader: ImageLoader,
    viewModel: GalleryViewModel,
    onSelectOtgDirectory: () -> Unit,
    onRequestFullAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onSetActivePreview: (FullscreenState?) -> Unit,
    onSetShowInfoDialog: (MediaItem?) -> Unit,
    onSetShowOtgGuide: (Boolean) -> Unit,
    previewCacheManager: PreviewCacheManager,
    archiveState: ArchiveState = ArchiveState(),
    restoreState: RestoreState = RestoreState()
) {
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val context = LocalContext.current

    // ... existing content ...
    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
                        GooglePhotosTopBar(selectedCount = selectedIds.size, isOtgConnected = isOtgConnected, otgUriSet = otgDirectoryUri != null, onClearSelection = onClearSelection, onSelectOtgClick = onSelectOtgDirectory)

                        // Тонкий прогресс-бар архивации/восстановления
            if (archiveState.isArchiving) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { archiveState.progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        strokeCap = StrokeCap.Round
                    )
                    val queue = if (archiveState.pendingQueueSize > 0) " +${archiveState.pendingQueueSize} в очереди" else ""
                    Text(
                        text = "Архивация: ${archiveState.currentFileIndex}/${archiveState.totalFiles} — ${archiveState.currentFileName}$queue",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp)
                    )
                }
            } else if (restoreState.isRestoring) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { restoreState.progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "Восстановление: ${restoreState.currentFileIndex}/${restoreState.totalFiles} — ${restoreState.currentFileName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp)
                    )
                }
            }

            val driveStatus by viewModel.otgManager.status.collectAsState()
            if (driveStatus == DriveStatus.UNKNOWN_DRIVE_CONNECTED) UnknownDriveBanner()
            else if (driveStatus == DriveStatus.KNOWN_DRIVE_DISCONNECTED && otgDirectoryUri != null) UnknownDriveBanner()
            if (hasPartialAccess) PartialAccessBanner(onGrantFullAccess = onRequestFullAccess, onOpenSettings = onOpenSettings)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentScreenRoute) {
                    "photos" -> PhotosRoute(viewModel = viewModel, selectedIds = selectedIds, imageLoader = imageLoader, isOtgConnected = isOtgConnected,
                        onItemClick = { item ->
                            if (selectedIds.isNotEmpty()) {
                                viewModel.toggleSelection(item.id)
                            } else {
                                val grouped = viewModel.groupedMediaItems.value
                                val allMediaItems = grouped.mapNotNull { (it as? GalleryItem.Media)?.item }
                                val index = allMediaItems.indexOfFirst { it.id == item.id }
                                if (index >= 0) {
                                    onSetActivePreview(FullscreenState(
                                        items = allMediaItems,
                                        initialIndex = index,
                                        sourceTab = SourceTab.PHOTOS
                                    ))
                                }
                            }
                        },
                        onItemLongClick = { item -> viewModel.toggleSelection(item.id) })
                    "archive" -> ArchiveRoute(viewModel = viewModel, selectedIds = selectedIds, imageLoader = imageLoader, isOtgConnected = isOtgConnected,
                        onItemClick = { item ->
                            if (isOtgConnected) {
                                if (selectedIds.isNotEmpty()) {
                                    viewModel.toggleSelection(item.id)
                                } else {
                                    val grouped = viewModel.archivedGroupedItems.value
                                    val allMediaItems = grouped.mapNotNull { (it as? GalleryItem.Media)?.item }
                                    val index = allMediaItems.indexOfFirst { it.id == item.id }
                                    if (index >= 0) {
                                        onSetActivePreview(FullscreenState(
                                            items = allMediaItems,
                                            initialIndex = index,
                                            sourceTab = SourceTab.ARCHIVE
                                        ))
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Подключите OTG накопитель для доступа к файлам", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onItemLongClick = { item ->
                            if (isOtgConnected) {
                                viewModel.toggleSelection(item.id)
                            } else {
                                Toast.makeText(context, "Подключите OTG накопитель для доступа к файлам", Toast.LENGTH_SHORT).show()
                            }
                        })
                    "settings" -> {
                        val physicalArchiveSize by viewModel.physicalArchiveSize.collectAsState()
                        SettingsTab(
                            onSelectOtgDirectory = onSelectOtgDirectory,
                            onClearCache = { viewModel.clearPreviewCache() },
                            isOtgConnected = isOtgConnected,
                            otgDirectoryDisplayName = viewModel.getOtgDirectoryDisplayName(),
                            cacheSize = previewCacheManager.getCacheSize(),
                            cacheFilesCount = previewCacheManager.getCacheFileCount(),
                            isLocalFolder = viewModel.isOtgLocalFolder(),
                            currentArchiveSize = physicalArchiveSize,
                            isLimitActive = viewModel.isLimitActive
                        )
                    }
                }
            }
        }

        showInfoDialogItem?.let { item ->
            InfoDialog(item = item, imageLoader = imageLoader ?: return@let, isOtgConnected = isOtgConnected,
                onOpenFullscreen = {
                    // Open fullscreen from info: just this single item
                    onSetActivePreview(FullscreenState(
                        items = listOf(item),
                        initialIndex = 0,
                        sourceTab = SourceTab.PHOTOS
                    ))
                }, onDismiss = { onSetShowInfoDialog(null) })
        }

        if (showOtgGuideDialog) {
            OtgGuideDialog(onConfirm = { onSetShowOtgGuide(false); onSelectOtgDirectory() }, onDismiss = { onSetShowOtgGuide(false) })
        }

        missingFilesNotification?.let { missingNames ->
            MissingFilesDialog(missingNames = missingNames, onDismiss = { viewModel.dismissMissingFilesNotification() })
        }

        activePreviewState?.let { state ->
            FullscreenPreview(
                state = state,
                imageLoader = imageLoader ?: return@let,
                isOtgConnected = isOtgConnected,
                otgDirectoryUri = otgDirectoryUri,
                onClose = {
                    onSetActivePreview(null)
                },
                onShowInfo = { item -> onSetShowInfoDialog(item) },
                onDeleteImmediate = { item -> viewModel.deleteSingleItemImmediate(item) },
                onArchiveSingle = { item, uri -> viewModel.archiveSingleItem(item, uri) },
                onRestoreSingle = { item -> viewModel.restoreSingleItem(item) }
            )
        }

        pendingDelete?.let { items ->
            DeleteConfirmDialog(
                pendingDelete = items,
                isArchiveTab = currentScreenRoute == "archive",
                isOtgConnected = isOtgConnected,
                onConfirm = { viewModel.confirmDelete() },
                onDismiss = { viewModel.dismissDelete() }
            )
        }

        archiveState.error?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissArchiveError() },
                title = { Text("Ошибка архивирования") },
                text = { Text(err) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissArchiveError() }) {
                        Text("ОК")
                    }
                }
            )
        }

        restoreState.error?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissRestoreError() },
                title = { Text("Ошибка восстановления") },
                text = { Text(err) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissRestoreError() }) {
                        Text("ОК")
                    }
                }
            )
        }

        val showFirstLaunch by viewModel.otgManager.showFirstLaunchDialog.collectAsState()
        if (showFirstLaunch) {
            val isUsbPhysical by viewModel.otgManager.physicalConnected.collectAsState()
            FirstLaunchDialog(
                isOtgConnected = isUsbPhysical,
                onStart = {
                    viewModel.dismissFirstLaunchDialog()
                    onSelectOtgDirectory()
                },
                onDismiss = { viewModel.dismissFirstLaunchDialog() }
            )
        }

        val showUnknownDrive by viewModel.otgManager.showUnknownDriveDialog.collectAsState()
        if (showUnknownDrive) {
            UnknownDriveDialog(
                onCreateNew = { viewModel.createNewArchive() },
                onDismiss = { viewModel.dismissUnknownDriveDialog() }
            )
        }

        val showLimitReachedDialog by viewModel.showLimitReachedDialog.collectAsState()
        if (showLimitReachedDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissLimitReachedDialog() },
                title = { Text("Лимит бесплатной версии", fontWeight = FontWeight.Bold) },
                text = { Text("Вы достигли лимита бесплатной версии в 128 МБ. Для продолжения архивации необходимо приобрести PRO версию либо удалить часть фото из архива.") },
                confirmButton = {
                    Button(onClick = { viewModel.dismissLimitReachedDialog() }) {
                        Text("ОК")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        Toast.makeText(context, "Покупка PRO версии временно недоступна", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Купить PRO")
                    }
                }
            )
        }
    }
}





