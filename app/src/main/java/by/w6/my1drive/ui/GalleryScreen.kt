package by.w6.my1drive.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import by.w6.my1drive.domain.model.MediaStatus
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.ui.components.BottomNavigationBar
import by.w6.my1drive.ui.components.SideNavigationBar
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import by.w6.my1drive.utils.PreviewCacheManager
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import by.w6.my1drive.utils.OtgThumbnailFetcher
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize

@Composable
fun GalleryScreen(
    onSelectOtgDirectory: () -> Unit,
    onSelectDeviceDirectory: () -> Unit = {},
    onPickRestoreFolder: () -> Unit = {},
    viewModel: GalleryViewModel = viewModel(),
    hasPartialAccess: Boolean = false,
    onRequestFullAccess: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val selectedIds by viewModel.selectedIds.collectAsState()
    val otgDirectoryUri by viewModel.otgDirectoryUri.collectAsState()
    val isOtgConnected by viewModel.isOtgConnected.collectAsState()
    val missingFilesNotification by viewModel.missingFilesNotification.collectAsState()
    val autoSyncAddedCount by viewModel.autoSyncAddedCount.collectAsState()
    val restoreRequest by viewModel.restoreRequest.collectAsState()
    val archiveState by viewModel.archiveState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val syncProgressState by viewModel.syncProgressState.collectAsState()
    val showRestorePicker = restoreRequest != null
    val mediaItems by viewModel.mediaItems.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var currentScreenRoute by remember { mutableStateOf("photos") }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var selectionOriginRoute by remember { mutableStateOf<String?>(null) }
    var activePreviewState by remember { mutableStateOf<FullscreenState?>(null) }
    var showInfoDialogItem by remember { mutableStateOf<MediaItem?>(null) }
    var showOtgGuideDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val previewDir = remember { File(context.filesDir, PreviewCacheManager.PREVIEW_DIR) }
    val imageLoader = remember(isOtgConnected) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                add(OtgThumbnailFetcher.Factory(
                    previewDir = previewDir,
                    onCached = { hash, path -> viewModel.onPreviewCached(hash, path) }
                ))
            }
            .crossfade(true)
            .build()
    }
    val previewCache = viewModel.getPreviewCacheManager()

    LaunchedEffect(selectedIds) {
        if (selectedIds.isNotEmpty()) {
            if (selectionOriginRoute == null) {
                selectionOriginRoute = currentScreenRoute
            }
        } else {
            selectionOriginRoute = null
        }
    }

    // Trigger SAF folder picker when restore items lack originalRelativePath
    LaunchedEffect(showRestorePicker) {
        if (showRestorePicker) {
            onPickRestoreFolder()
        }
    }

    Scaffold(
        bottomBar = {
            if (!isLandscape) {
                BottomNavigationBar(
                    currentRoute = currentScreenRoute,
                    onNavigate = { route ->
                        if (activePreviewState != null) {
                            activePreviewState = null
                        }
                        if (route != "settings" && selectionOriginRoute != null && selectionOriginRoute != route) {
                            viewModel.clearSelection()
                        }
                        currentScreenRoute = route
                    }
                )
            }
        }
    ) { paddingValues ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (isLandscape) {
                SideNavigationBar(
                    currentRoute = currentScreenRoute,
                    onNavigate = { route ->
                        if (activePreviewState != null) {
                            activePreviewState = null
                        }
                        if (route != "settings" && selectionOriginRoute != null && selectionOriginRoute != route) {
                            viewModel.clearSelection()
                        }
                        currentScreenRoute = route
                    }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                GalleryScreenContent(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    selectedIds = selectedIds,
                    otgDirectoryUri = otgDirectoryUri,
                    isOtgConnected = isOtgConnected,
                    hasPartialAccess = hasPartialAccess,
                    currentScreenRoute = currentScreenRoute,
                    missingFilesNotification = missingFilesNotification,
                    autoSyncAddedCount = autoSyncAddedCount,
                    activePreviewState = activePreviewState,
                    showInfoDialogItem = showInfoDialogItem,
                    showOtgGuideDialog = showOtgGuideDialog,
                    imageLoader = imageLoader,
                    viewModel = viewModel,
                    onSelectOtgDirectory = onSelectOtgDirectory,
                    onSelectDeviceDirectory = onSelectDeviceDirectory,
                    onRequestFullAccess = onRequestFullAccess,
                    onOpenSettings = onOpenSettings,
                    onClearSelection = { viewModel.clearSelection() },
                    onSetActivePreview = { state -> activePreviewState = state },
                    onSetShowInfoDialog = { showInfoDialogItem = it },
                    onSetShowOtgGuide = { showOtgGuideDialog = it },
                    previewCacheManager = previewCache,
                    archiveState = archiveState,
                    restoreState = restoreState,
                    syncProgressState = syncProgressState,
                    onSyncArchive = { viewModel.syncArchive() },
                    onSelectDateRangeClick = { showDateRangePicker = true }
                )

                val visibleItems = remember(currentScreenRoute, mediaItems) {
                    if (currentScreenRoute == "photos") {
                        mediaItems.filter { it.status == MediaStatus.ON_DEVICE }
                    } else {
                        mediaItems.filter {
                            it.status == MediaStatus.ARCHIVED_OTG &&
                            (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/"))
                        }
                    }
                }
                val firstSelectedItem = remember(selectedIds, mediaItems) {
                    mediaItems.firstOrNull { it.id in selectedIds }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedIds.isNotEmpty() && currentScreenRoute != "settings",
                    enter = slideInVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) { it } + fadeIn(),
                    exit = slideOutVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .padding(bottom = paddingValues.calculateBottomPadding()),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        )
                    ) {
                        GalleryScreenActionBar(
                            isArchiveTab = currentScreenRoute == "archive",
                            isOtgConnected = isOtgConnected,
                            otgDirectoryUri = otgDirectoryUri,
                            onArchive = { otgDirectoryUri?.let { viewModel.startArchiving(it) } },
                            onRestore = { viewModel.requestRestore() }
                        )
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        val visibleItems = remember(currentScreenRoute, mediaItems) {
            if (currentScreenRoute == "photos") {
                mediaItems.filter { it.status == MediaStatus.ON_DEVICE }
            } else {
                mediaItems.filter {
                    it.status == MediaStatus.ARCHIVED_OTG &&
                    (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/"))
                }
            }
        }
        DateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onDateRangeSelected = { startMillis, endMillis ->
                showDateRangePicker = false
                if (startMillis != null && endMillis != null) {
                    val startSec = startMillis / 1000
                    val endSec = (endMillis / 1000) + 86399
                    val matching = visibleItems.filter { item ->
                        item.dateModified in startSec..endSec
                    }.map { it.id }
                    viewModel.selectItems(matching)
                }
            }
        )
    }
}

