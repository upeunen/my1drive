package by.w6.my1drive.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.composable
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.runtime.mutableStateMapOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.ui.GalleryItem
import by.w6.my1drive.utils.PreviewCacheManager
import coil.ImageLoader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Sync

import by.w6.my1drive.ui.components.UnknownDriveBanner
import by.w6.my1drive.ui.components.DisconnectedDriveBanner
import by.w6.my1drive.ui.components.PartialAccessBanner
import by.w6.my1drive.ui.components.OtgRequiredBanner
import by.w6.my1drive.ui.components.ConnectingUsbBanner
import by.w6.my1drive.ui.components.ProgressPanel
import by.w6.my1drive.ui.components.mapStepToText

@Composable
fun MissingFilesDialog(missingNames: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_sync_missing_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.auto_sync_missing_msg, missingNames.joinToString("\n"))) },
        confirmButton = { Button(onClick = onDismiss) { Text(text = stringResource(R.string.btn_ok), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false) } }
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
                Text("РќРµРёР·РІРµСЃС‚РЅС‹Р№ РЅРѕСЃРёС‚РµР»СЊ", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text("РџРѕРґРєР»СЋС‡РµРЅ РЅРµРёР·РІРµСЃС‚РЅС‹Р№ РЅРѕСЃРёС‚РµР»СЊ. РЎРѕР·РґР°С‚СЊ РЅРѕРІС‹Р№ Р°СЂС…РёРІ, РёР»Рё РµСЃР»Рё РІРµСЂРЅС‘С‚СЃСЏ СЃС‚Р°СЂС‹Р№ вЂ” СЃРјРѕР¶РµС‚Рµ РµРіРѕ СЃРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°С‚СЊ")
        },
        confirmButton = {
            Button(onClick = onCreateNew) {
                Text(
                    text = "РЎРѕР·РґР°С‚СЊ РЅРѕРІС‹Р№",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Р—Р°РєСЂС‹С‚СЊ",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
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
                Text(
                    text = if (pendingDelete.size == 1) {
                        if (isArchiveTab) stringResource(R.string.btn_delete_otg) else stringResource(R.string.btn_delete_file)
                    } else {
                        stringResource(R.string.action_delete_all)
                    },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    )
}

@Composable
fun GalleryScreenContent(
    modifier: Modifier,
    navController: androidx.navigation.NavHostController,
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
    onSelectDeviceDirectory: () -> Unit = {},
    onRequestFullAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleSelection: (String) -> Unit = {},
    onSetActivePreview: (FullscreenState?) -> Unit,
    onNavigateToTab: (String) -> Unit = {},
    onSetShowInfoDialog: (MediaItem?) -> Unit,
    onSetShowOtgGuide: (Boolean) -> Unit,
    previewCacheManager: PreviewCacheManager,
    archiveState: ArchiveState = ArchiveState(),
    restoreState: RestoreState = RestoreState(),
    syncProgressState: SyncProgressState = SyncProgressState(),
    actionBarHeightPx: Float = 0f,
    onSyncArchive: () -> Unit = {},
    onSelectDateRangeClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
      val uiState by viewModel.uiState.collectAsState()
    val pendingDelete = uiState.pendingDelete
    val activeArchiveUuid = uiState.activeArchiveUuid
    val knownArchives by viewModel.knownArchives.collectAsState()
    val isSharingPreparing = uiState.isSharingPreparing
    val isCheckingConnection = uiState.isCheckingConnection
    val isSilentSyncing = uiState.isSilentSyncing

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("my1drive_prefs", android.content.Context.MODE_PRIVATE) }
    var showDisconnectedOtgItemInfo by remember { mutableStateOf<MediaItem?>(null) }
    var showDebugLogsDialog by remember { mutableStateOf(false) }
    var showEjectConfirmDialog by remember { mutableStateOf(false) }
    var showOfflineShareConfirm by remember { mutableStateOf(false) }
    val groupedItems = uiState.groupedItems
    val archivedGroupedItems = uiState.archivedGroupedItems
    val mediaItems = uiState.mediaItems
    val gridColumnsCount = uiState.gridColumnsCount

    var showChangeFolderConfirmDialog by remember { mutableStateOf(false) }

    if (showOfflineShareConfirm) {
        var dontWarnAgain by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showOfflineShareConfirm = false },
            title = { Text("РџРѕРґРµР»РёС‚СЊСЃСЏ СЌСЃРєРёР·Р°РјРё?") },
            text = {
                Column {
                    Text("РќРµРєРѕС‚РѕСЂС‹Рµ РёР· РІС‹Р±СЂР°РЅРЅС‹С… С„Р°Р№Р»РѕРІ РЅР°С…РѕРґСЏС‚СЃСЏ РЅР° РѕС‚РєР»СЋС‡РµРЅРЅС‹С… РЅР°РєРѕРїРёС‚РµР»СЏС…. Р‘СѓРґСѓС‚ РѕС‚РїСЂР°РІР»РµРЅС‹ РёС… СЌСЃРєРёР·С‹ РЅРёР·РєРѕРіРѕ СЂР°Р·СЂРµС€РµРЅРёСЏ.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dontWarnAgain = !dontWarnAgain }
                    ) {
                        Checkbox(
                            checked = dontWarnAgain,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("СЏ РїРѕРЅРёРјР°СЋ, Р±РѕР»СЊС€Рµ РЅРµ РЅСѓР¶РЅРѕ РїСЂРµРґСѓРїСЂРµР¶РґР°С‚СЊ", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showOfflineShareConfirm = false
                    if (dontWarnAgain) {
                        prefs.edit().putBoolean("skip_offline_share_warning", true).apply()
                    }
                    viewModel.shareSelectedItems(context) { errMsg ->
                        android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("РџРѕРґРµР»РёС‚СЊСЃСЏ", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineShareConfirm = false }) {
                    Text("РћС‚РјРµРЅР°", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    if (showEjectConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEjectConfirmDialog = false },
            title = { Text(stringResource(R.string.dialog_eject_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.dialog_eject_message)) },
            confirmButton = {
                Button(onClick = {
                    showEjectConfirmDialog = false
                    viewModel.ejectOtg()
                }) {
                    Text(stringResource(R.string.action_eject), maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEjectConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel), maxLines = 1, softWrap = false)
                }
            }
        )
    }

    if (showChangeFolderConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showChangeFolderConfirmDialog = false },
            title = { Text("РЎРјРµРЅРёС‚СЊ РїР°РїРєСѓ Р°СЂС…РёРІР°?", fontWeight = FontWeight.Bold) },
            text = { Text("РЎРјРµРЅР° РїР°РїРєРё Р°СЂС…РёРІР° РјРѕР¶РµС‚ РЅР°СЂСѓС€РёС‚СЊ С‚РµРєСѓС‰СѓСЋ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёСЋ. Р’С‹ СѓРІРµСЂРµРЅС‹, С‡С‚Рѕ С…РѕС‚РёС‚Рµ РїСЂРѕРґРѕР»Р¶РёС‚СЊ?") },
            confirmButton = {
                Button(onClick = {
                    showChangeFolderConfirmDialog = false
                    onSelectOtgDirectory()
                }) {
                    Text("РЎРјРµРЅРёС‚СЊ", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeFolderConfirmDialog = false }) {
                    Text("РћС‚РјРµРЅР°", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // Selection group chips state
    var isGroupExpanded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(selectedIds) {
        if (selectedIds.isEmpty()) isGroupExpanded = false
    }

    val deleteEnabled = if (currentScreenRoute == "archive") isOtgConnected else true

    val visibleItemsForChips = remember(currentScreenRoute, mediaItems) {
        if (currentScreenRoute == "photos") {
            mediaItems.filter { it.status == by.w6.my1drive.domain.model.MediaStatus.ON_DEVICE }
        } else {
            mediaItems.filter { it.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG }
        }
    }
    val firstSelectedItem = remember(selectedIds, mediaItems) {
        mediaItems.firstOrNull { it.id in selectedIds }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GooglePhotosTopBar(
                selectedCount = selectedIds.size,
                isOtgConnected = isOtgConnected,
                otgUriSet = otgDirectoryUri != null,
                isGroupExpanded = isGroupExpanded,
                deleteEnabled = deleteEnabled,
                onClearSelection = onClearSelection,
                onEjectClick = { showEjectConfirmDialog = true },
                onGroupClick = { isGroupExpanded = !isGroupExpanded },
                onShare = {
                    val selected = mediaItems.filter { it.id in selectedIds }
                    val hasOffline = selected.any { 
                        it.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG && 
                        !(isOtgConnected && it.archiveUuid == activeArchiveUuid)
                    }
                    if (hasOffline && !prefs.getBoolean("skip_offline_share_warning", false)) {
                        showOfflineShareConfirm = true
                    } else {
                        viewModel.shareSelectedItems(context) { errMsg ->
                            android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDelete = { viewModel.requestDeleteSelected() },
                gridColumnsCount = gridColumnsCount,
                onToggleGridColumns = { viewModel.setGridColumnsCount(if (gridColumnsCount == 3) 4 else 3) },
                showGridToggle = currentScreenRoute != "settings"
            )

            ConnectingUsbBanner(visible = isCheckingConnection || isSilentSyncing)

            // Expandable group chips row
            AnimatedVisibility(
                visible = isGroupExpanded && selectedIds.isNotEmpty(),
                enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeIn(),
                exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Р’СЃРµ
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            viewModel.selectItems(visibleItemsForChips.map { it.id })
                            isGroupExpanded = false
                        },
                        label = { Text("Р’СЃРµ") }
                    )
                    // РЎ РґР°С‚РѕР№
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            if (firstSelectedItem != null) {
                                val targetDate = firstSelectedItem.dateModified
                                val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = targetDate * 1000 }
                                val matching = visibleItemsForChips.filter { item ->
                                    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = item.dateModified * 1000 }
                                    cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                                        cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
                                }.map { it.id }
                                viewModel.selectItems(matching)
                            }
                            isGroupExpanded = false
                        },
                        enabled = firstSelectedItem != null,
                        label = { Text("РЎ РґР°С‚РѕР№") }
                    )
                    // Р’ РїР°РїРєРµ
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            if (firstSelectedItem != null) {
                                val path = firstSelectedItem.originalRelativePath
                                val matching = visibleItemsForChips
                                    .filter { it.originalRelativePath == path }
                                    .map { it.id }
                                viewModel.selectItems(matching)
                            }
                            isGroupExpanded = false
                        },
                        enabled = firstSelectedItem != null && !firstSelectedItem.originalRelativePath.isNullOrEmpty(),
                        label = { Text("Р’ РїР°РїРєРµ") }
                    )
                    // Р”РёР°РїР°Р·РѕРЅ
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            onSelectDateRangeClick()
                            isGroupExpanded = false
                        },
                        label = { Text("Р”РёР°РїР°Р·РѕРЅ") }
                    )
                }
            }

            // РџСЂРѕРіСЂРµСЃСЃ-РїР°РЅРµР»СЊ Р°СЂС…РёРІР°С†РёРё/РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёСЏ/СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёРё
            if (archiveState.isArchiving) {
                val queue = if (archiveState.pendingQueueSize > 0) " (+${archiveState.pendingQueueSize} РІ РѕС‡РµСЂРµРґРё)" else ""
                ProgressPanel(
                    title = "РђСЂС…РёРІР°С†РёСЏ",
                    fileName = archiveState.currentFileName,
                    currentIndex = archiveState.currentFileIndex,
                    totalFiles = archiveState.totalFiles,
                    progressFraction = archiveState.progressFraction,
                    extraInfo = queue,
                    icon = Icons.Default.CloudUpload,
                    statusText = mapStepToText(archiveState.currentStep),
                    onCancel = { viewModel.cancelArchiving() }
                )
            } else if (restoreState.isRestoring) {
                ProgressPanel(
                    title = "Р’РѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёРµ",
                    fileName = restoreState.currentFileName,
                    currentIndex = restoreState.currentFileIndex,
                    totalFiles = restoreState.totalFiles,
                    progressFraction = restoreState.progressFraction,
                    icon = Icons.Default.CloudDownload,
                    statusText = mapStepToText(restoreState.currentStep),
                    onCancel = { viewModel.cancelRestoring() }
                )
            } else if (syncProgressState.isSyncing) {
                ProgressPanel(
                    title = "РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ Р°СЂС…РёРІР°",
                    fileName = syncProgressState.currentFileName,
                    currentIndex = syncProgressState.currentFileIndex,
                    totalFiles = syncProgressState.totalFiles,
                    progressFraction = syncProgressState.progressFraction,
                    icon = Icons.Default.Sync,
                    statusText = if (syncProgressState.totalFiles > 0) "Р’С‹С‡РёСЃР»РµРЅРёРµ С…СЌС€РµР№..." else "РџРѕРёСЃРє С„Р°Р№Р»РѕРІ..."
                )
            }

            val driveStatus by viewModel.otgManager.status.collectAsState()
            if (driveStatus == DriveStatus.UNKNOWN_DRIVE_CONNECTED) UnknownDriveBanner()
            else if (driveStatus == DriveStatus.KNOWN_DRIVE_DISCONNECTED && otgDirectoryUri != null && !(isCheckingConnection || isSilentSyncing)) DisconnectedDriveBanner()
            if (hasPartialAccess) PartialAccessBanner(onGrantFullAccess = onRequestFullAccess, onOpenSettings = onOpenSettings)
            if (otgDirectoryUri == null) OtgRequiredBanner()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                androidx.navigation.compose.NavHost(
                    navController = navController,
                    startDestination = "photos",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) },
                    exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) }
                ) {
                    composable("photos") {
                        PhotosRoute(
                            viewModel = viewModel,
                            selectedIds = selectedIds,
                            imageLoader = imageLoader,
                            isOtgConnected = isOtgConnected,
                            gridColumnsCount = gridColumnsCount,
                            actionBarHeightPx = actionBarHeightPx,
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
                            onItemLongClick = { item -> viewModel.toggleSelection(item.id) },
                            onScrollStateChanged = { viewModel.setScrolling(it) }
                        )
                    }
                    composable("archive") {
                        ArchiveRoute(
                            viewModel = viewModel,
                            selectedIds = selectedIds,
                            imageLoader = imageLoader,
                            isOtgConnected = isOtgConnected,
                            gridColumnsCount = gridColumnsCount,
                            actionBarHeightPx = actionBarHeightPx,
                            onItemClick = { item ->
                                val currentActiveUuid = viewModel.otgManager.activeArchiveUuid.value
                                val isItemActive = isOtgConnected && item.archiveUuid == currentActiveUuid
                                if (selectedIds.isNotEmpty()) {
                                    if (isItemActive || item.hasCachedPreview) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        showDisconnectedOtgItemInfo = item
                                    }
                                } else {
                                    if (isItemActive) {
                                        val grouped = viewModel.archivedGroupedItems.value
                                        val allMediaItems = grouped.mapNotNull { (it as? GalleryItem.Media)?.item }
                                        val filteredItems = allMediaItems.filter { m -> 
                                            m.status != by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG || (isOtgConnected && m.archiveUuid == currentActiveUuid)
                                        }
                                        val index = filteredItems.indexOfFirst { it.id == item.id }
                                        if (index >= 0) {
                                            onSetActivePreview(FullscreenState(
                                                items = filteredItems,
                                                initialIndex = index,
                                                sourceTab = SourceTab.ARCHIVE
                                            ))
                                        }
                                    } else {
                                        showDisconnectedOtgItemInfo = item
                                    }
                                }
                            },
                            onItemLongClick = { item ->
                                val currentActiveUuid = viewModel.otgManager.activeArchiveUuid.value
                                val isItemActive = isOtgConnected && item.archiveUuid == currentActiveUuid
                                if (isItemActive || item.hasCachedPreview) {
                                    viewModel.toggleSelection(item.id)
                                } else {
                                    Toast.makeText(context, "РџРѕРґРєР»СЋС‡РёС‚Рµ РЅСѓР¶РЅС‹Р№ OTG РЅР°РєРѕРїРёС‚РµР»СЊ", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onScrollStateChanged = { viewModel.setScrolling(it) }
                        )
                    }
                    composable("settings") {
                            val physicalArchiveSize = uiState.physicalArchiveSize
                            val otgDirectoryDisplayName = uiState.otgDirectoryDisplayName
                            val activeArchiveUuid = uiState.activeArchiveUuid
                            val isSyncingThumbnails = uiState.isSyncingThumbnails
                            val syncThumbnailsProgress by viewModel.syncThumbnailsProgress.collectAsState()
                            val missingThumbnailsCount = uiState.missingThumbnailsCount
                            val isStorageLow = uiState.isStorageLow

                            LaunchedEffect(Unit) {
                                viewModel.updateMissingThumbnailsCount()
                            }

                            SettingsTab(
                                onSelectOtgDirectory = { showChangeFolderConfirmDialog = true },
                                onClearCache = { viewModel.clearPreviewCache() },
                                isOtgConnected = isOtgConnected,
                                otgDirectoryDisplayName = otgDirectoryDisplayName,
                                cacheSize = previewCacheManager.getCacheSize(),
                                cacheFilesCount = previewCacheManager.getCacheFileCount(),
                                isLocalFolder = viewModel.isOtgLocalFolder(),
                                currentArchiveSize = physicalArchiveSize,
                                isLimitActive = viewModel.isLimitActive,
                                vpsManager = viewModel.vpsManager,
                                onShowDebugLogs = { showDebugLogsDialog = true },
                                onSyncArchive = onSyncArchive,
                                onRefresh = { viewModel.refresh() },
                                knownArchives = knownArchives,
                                onDeleteArchive = { viewModel.deleteArchive(it) },
                                activeArchiveUuid = activeArchiveUuid,
                                isSyncingThumbnails = isSyncingThumbnails,
                                syncThumbnailsProgress = syncThumbnailsProgress,
                                missingThumbnailsCount = missingThumbnailsCount,
                                onSyncThumbnails = { viewModel.startThumbnailSync() },
                                onCancelSyncThumbnails = { viewModel.cancelThumbnailSync() },
                                isStorageLow = isStorageLow
                            )
                    }
                }
            }
        }

        showInfoDialogItem?.let { item ->
            val archive = remember(knownArchives, item.archiveUuid) {
                knownArchives.find { it.uuid == item.archiveUuid }
            }
            InfoDialog(
                item = item,
                imageLoader = imageLoader ?: return@let,
                isOtgConnected = if (item.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG) (isOtgConnected && item.archiveUuid == activeArchiveUuid) else isOtgConnected,
                archive = archive,
                onOpenFullscreen = {
                    if (activePreviewState == null) {
                        // Open fullscreen from info: just this single item
                        onSetActivePreview(FullscreenState(
                            items = listOf(item),
                            initialIndex = 0,
                            sourceTab = if (currentScreenRoute == "archive") SourceTab.ARCHIVE else SourceTab.PHOTOS
                        ))
                    } else {
                        // Already in fullscreen, just close the properties dialog so we can continue swiping
                        onSetShowInfoDialog(null)
                    }
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
                activeArchiveUuid = activeArchiveUuid,
                selectedIds = selectedIds,
                onToggleSelection = onToggleSelection,
                onClose = {
                    onSetActivePreview(null)
                },
                onShowInfo = { item -> onSetShowInfoDialog(item) },
                onDeleteImmediate = { item -> viewModel.deleteSingleItemImmediate(item) },
                onArchiveSingle = { item, uri -> viewModel.archiveSingleItem(item, uri) },
                onRestoreSingle = { item -> viewModel.restoreSingleItem(item) },
                onShare = { item ->
                    viewModel.shareMediaItem(item, context) { errMsg ->
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    }
                },
                isSharingPreparing = isSharingPreparing,
                isArchiving = archiveState.isArchiving
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
                title = { Text("РћС€РёР±РєР° Р°СЂС…РёРІРёСЂРѕРІР°РЅРёСЏ") },
                text = { Text(err) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissArchiveError() }) {
                        Text(
                            text = "РћРљ",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                dismissButton = {
                    val context = LocalContext.current
                    androidx.compose.material3.TextButton(onClick = {
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Log", err))
                    }) {
                        Text("РЎРєРѕРїРёСЂРѕРІР°С‚СЊ Р»РѕРі")
                    }
                }
            )
        }

        restoreState.error?.let { err ->
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { viewModel.dismissRestoreError() },
                title = { Text("РћС€РёР±РєР° РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёСЏ") },
                text = { Text(err) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissRestoreError() }) {
                        Text(
                            text = "РћРљ",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Log", err))
                    }) {
                        Text("РЎРєРѕРїРёСЂРѕРІР°С‚СЊ Р»РѕРі")
                    }
                }
            )
        }

        val syncState by viewModel.syncState.collectAsState()
        syncState?.let { stateMessage ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissSync() },
                title = { Text(stringResource(R.string.sync_archive_title)) },
                text = { Text(stateMessage) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissSync() }) {
                        Text(
                            text = "РћРљ",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                dismissButton = {
                    if (stateMessage.contains("РћС€РёР±РєР°", ignoreCase = true)) {
                        val context = LocalContext.current
                        androidx.compose.material3.TextButton(onClick = {
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Log", stateMessage))
                        }) {
                            Text("РЎРєРѕРїРёСЂРѕРІР°С‚СЊ Р»РѕРі")
                        }
                    }
                }
            )
        }

        val activeDialog = uiState.activeDialog
        by.w6.my1drive.ui.components.dialogs.AppDialogCoordinator(
            activeDialog = activeDialog,
            viewModel = viewModel,
            currentScreenRoute = currentScreenRoute,
            onSelectOtgDirectory = onSelectOtgDirectory,
            onSelectDeviceDirectory = onSelectDeviceDirectory,
            onNavigateToTab = onNavigateToTab
        )

        showDisconnectedOtgItemInfo?.let { item ->
            val archive = remember(knownArchives, item.archiveUuid) {
                knownArchives.find { it.uuid == item.archiveUuid }
            }
            DisconnectedOtgInfoDialog(
                item = item,
                imageLoader = imageLoader,
                archive = archive,
                onDismiss = { showDisconnectedOtgItemInfo = null }
            )
        }



        if (showDebugLogsDialog) {
            DebugLogsDialog(onDismiss = { showDebugLogsDialog = false })
        }
    }
}

@Composable
fun SelectionHelperChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    shape: Shape,
    enabled: Boolean = true,
    isRightAligned: Boolean = false,
    modifier: Modifier = Modifier
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val iconColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = if (isRightAligned) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRightAligned) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 1.15f),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 1.15f),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SelectionHelperPanel(
    firstSelectedItem: MediaItem?,
    visibleItems: List<MediaItem>,
    onSelectItems: (Collection<String>) -> Unit,
    onSelectDateRangeClick: () -> Unit,
    onClearSelection: () -> Unit
) {
    val hasSelected = firstSelectedItem != null
    val hasFolder = firstSelectedItem != null && !firstSelectedItem.originalRelativePath.isNullOrEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Р’Р«Р”Р•Р›РРўР¬",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chip 1: Р’СЃРµ
                    SelectionHelperChip(
                        text = "Р’СЃРµ",
                        icon = Icons.Outlined.CheckCircleOutline,
                        onClick = {
                            val allIds = visibleItems.map { it.id }
                            onSelectItems(allIds)
                        },
                        shape = ConcaveCutoutShape(CutoutCorner.BOTTOM_RIGHT),
                        modifier = Modifier.weight(1f)
                    )
                    // Chip 2: РЎ СЌС‚РѕР№ РґР°С‚РѕР№
                    SelectionHelperChip(
                        text = "РЎ РґР°С‚РѕР№",
                        icon = Icons.Outlined.CalendarToday,
                        enabled = hasSelected,
                        onClick = {
                            if (firstSelectedItem != null) {
                                val targetDate = firstSelectedItem.dateModified
                                val cal1 = Calendar.getInstance().apply { timeInMillis = targetDate * 1000 }
                                val matching = visibleItems.filter { item ->
                                    val cal2 = Calendar.getInstance().apply { timeInMillis = item.dateModified * 1000 }
                                    cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                                            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                                } .map { it.id }
                                onSelectItems(matching)
                            }
                        },
                        shape = ConcaveCutoutShape(CutoutCorner.BOTTOM_LEFT),
                        isRightAligned = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chip 3: Р’ СЌС‚РѕР№ РїР°РїРєРµ
                    SelectionHelperChip(
                        text = "Р’ РїР°РїРєРµ",
                        icon = Icons.Outlined.Folder,
                        enabled = hasFolder,
                        onClick = {
                            if (firstSelectedItem != null) {
                                val targetPath = firstSelectedItem.originalRelativePath
                                val matching = visibleItems.filter { it.originalRelativePath == targetPath } .map { it.id }
                                onSelectItems(matching)
                            }
                        },
                        shape = ConcaveCutoutShape(CutoutCorner.TOP_RIGHT),
                        modifier = Modifier.weight(1f)
                    )
                    // Chip 4: Р’С‹Р±СЂР°С‚СЊ РґРёР°РїР°Р·РѕРЅ
                    SelectionHelperChip(
                        text = "Р”РёР°РїР°Р·РѕРЅ",
                        icon = Icons.Outlined.DateRange,
                        onClick = onSelectDateRangeClick,
                        shape = ConcaveCutoutShape(CutoutCorner.TOP_LEFT),
                        isRightAligned = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Central circular button for clear/cancel selection (TV-remote style)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable { onClearSelection() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "РЎРЅСЏС‚СЊ РІС‹РґРµР»РµРЅРёРµ",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

enum class CutoutCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

class ConcaveCutoutShape(
    val cutoutCorner: CutoutCorner,
    val outerRadius: Dp = 12.dp,
    val cutoutRadius: Dp = 26.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val rOuter = with(density) { outerRadius.toPx() }
        val rCut = with(density) { cutoutRadius.toPx() }

        when (cutoutCorner) {
            CutoutCorner.BOTTOM_RIGHT -> {
                path.moveTo(0f, rOuter)
                path.arcTo(
                    rect = Rect(0f, 0f, rOuter * 2, rOuter * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w - rOuter, 0f)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, 0f, w, rOuter * 2),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w, h - rCut)
                path.arcTo(
                    rect = Rect(w - rCut, h - rCut, w + rCut, h + rCut),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                path.lineTo(rOuter, h)
                path.arcTo(
                    rect = Rect(0f, h - rOuter * 2, rOuter * 2, h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.close()
            }
            CutoutCorner.BOTTOM_LEFT -> {
                path.moveTo(rOuter, 0f)
                path.lineTo(w - rOuter, 0f)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, 0f, w, rOuter * 2),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w, h - rOuter)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, h - rOuter * 2, w, h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(rCut, h)
                path.arcTo(
                    rect = Rect(-rCut, h - rCut, rCut, h + rCut),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                path.lineTo(0f, rOuter)
                path.arcTo(
                    rect = Rect(0f, 0f, rOuter * 2, rOuter * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.close()
            }
            CutoutCorner.TOP_RIGHT -> {
                path.moveTo(0f, rOuter)
                path.arcTo(
                    rect = Rect(0f, 0f, rOuter * 2, rOuter * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w - rCut, 0f)
                path.arcTo(
                    rect = Rect(w - rCut, -rCut, w + rCut, rCut),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                path.lineTo(w, h - rOuter)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, h - rOuter * 2, w, h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(rOuter, h)
                path.arcTo(
                    rect = Rect(0f, h - rOuter * 2, rOuter * 2, h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.close()
            }
            CutoutCorner.TOP_LEFT -> {
                path.moveTo(rCut, 0f)
                path.lineTo(w - rOuter, 0f)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, 0f, w, rOuter * 2),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w, h - rOuter)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, h - rOuter * 2, w, h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(rOuter, h)
                path.arcTo(
                    rect = Rect(0f, h - rOuter * 2, rOuter * 2, h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(0f, rCut)
                path.arcTo(
                    rect = Rect(-rCut, -rCut, rCut, rCut),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                path.close()
            }
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (startDateMillis: Long?, endDateMillis: Long?) -> Unit
) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateRangeSelected(state.selectedStartDateMillis, state.selectedEndDateMillis)
                }
            ) {
                Text(
                    text = "Р’С‹Р±СЂР°С‚СЊ",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "РћС‚РјРµРЅР°",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    text = "Р’С‹Р±РµСЂРёС‚Рµ РґРёР°РїР°Р·РѕРЅ РґР°С‚",
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp)
                )
            },
            headline = {
                // Default headline behaves fine, but we can customize if needed
            },
            showModeToggle = false,
            modifier = Modifier.weight(1f)
        )
    }
}

fun mapStepToText(step: String): String {
    return when {
        step == "preparing" || step == "restore_preparing" -> "РџРѕРґРіРѕС‚РѕРІРєР°..."
        step == "verifying" || step == "restore_verifying" -> "РџСЂРѕРІРµСЂРєР° С†РµР»РѕСЃС‚РЅРѕСЃС‚Рё..."
        step == "copying" -> "РљРѕРїРёСЂРѕРІР°РЅРёРµ РІ Р°СЂС…РёРІ..."
        step == "restore_reading" -> "Р§С‚РµРЅРёРµ РёР· Р°СЂС…РёРІР°..."
        step == "restore_writing" -> "Р—Р°РїРёСЃСЊ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРѕ..."
        step.startsWith("restore_writing_percent:") -> {
            val pct = step.substringAfter(":")
            "Р—Р°РїРёСЃСЊ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРѕ... ($pct%)"
        }
        else -> "РћР±СЂР°Р±РѕС‚РєР°..."
    }
}

@Composable
fun ProgressPanel(
    title: String,
    fileName: String,
    currentIndex: Int,
    totalFiles: Int,
    progressFraction: Float,
    extraInfo: String = "",
    icon: ImageVector,
    statusText: String? = null,
    onCancel: (() -> Unit)? = null
) {

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val cardPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = cardPulseAlpha)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$title ($currentIndex РёР· $totalFiles)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (fileName.isNotEmpty()) {
                        Text(
                            text = fileName + extraInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!statusText.isNullOrEmpty()) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = StrokeCap.Round
                )
                if (onCancel != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    androidx.compose.material3.TextButton(
                        onClick = onCancel,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "РџСЂРµСЂРІР°С‚СЊ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}




