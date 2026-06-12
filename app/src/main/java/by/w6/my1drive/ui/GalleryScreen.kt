package by.w6.my1drive.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.ui.components.BottomNavigationBar
import by.w6.my1drive.utils.PreviewCacheManager
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import by.w6.my1drive.utils.OtgThumbnailFetcher
import java.io.File
import androidx.compose.foundation.layout.fillMaxSize

@Composable
fun GalleryScreen(
    onSelectOtgDirectory: () -> Unit,
    onPickRestoreFolder: () -> Unit = {},
    viewModel: GalleryViewModel = viewModel(),
    hasPartialAccess: Boolean = false,
    onRequestFullAccess: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val selectedIds by viewModel.selectedIds.collectAsState()
    val otgDirectoryUri by viewModel.otgDirectoryUri.collectAsState()
    val isOtgConnected by viewModel.isOtgConnected.collectAsState()
    val driveStatus by viewModel.driveStatus.collectAsState()
    val restoreRequest by viewModel.restoreRequest.collectAsState()
    val missingFilesNotification by viewModel.missingFilesNotification.collectAsState()
    val autoSyncAddedCount by viewModel.autoSyncAddedCount.collectAsState()
    val newArchiveId by viewModel.newArchiveId.collectAsState()

    var currentScreenRoute by remember { mutableStateOf("photos") }
    var activePreviewItem by remember { mutableStateOf<MediaItem?>(null) }
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

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                currentRoute = currentScreenRoute,
                onNavigate = { route -> currentScreenRoute = route }
            )
        },
        floatingActionButton = {
            if (selectedIds.isNotEmpty() && currentScreenRoute != "settings") {
                Card(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    GalleryScreenActionBar(
                        isArchiveTab = currentScreenRoute == "archive",
                        driveStatus = driveStatus,
                        otgDirectoryUri = otgDirectoryUri,
                        onDelete = { viewModel.deleteSelected() },
                        onArchive = { viewModel.startArchiving(otgDirectoryUri!!) },
                        onRestore = { viewModel.requestRestore() }
                    )
                }
            }
        }
    ) { paddingValues ->
        GalleryScreenContent(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            selectedIds = selectedIds,
            driveStatus = driveStatus,
            otgDirectoryUri = otgDirectoryUri,
            isOtgConnected = isOtgConnected,
            hasPartialAccess = hasPartialAccess,
            currentScreenRoute = currentScreenRoute,
            newArchiveId = newArchiveId,
            missingFilesNotification = missingFilesNotification,
            autoSyncAddedCount = autoSyncAddedCount,
            activePreviewItem = activePreviewItem,
            showInfoDialogItem = showInfoDialogItem,
            showOtgGuideDialog = showOtgGuideDialog,
            imageLoader = imageLoader,
            viewModel = viewModel,
            onSelectOtgDirectory = onSelectOtgDirectory,
            onRequestFullAccess = onRequestFullAccess,
            onOpenSettings = onOpenSettings,
            onClearSelection = { viewModel.clearSelection() },
            onSetActivePreview = { activePreviewItem = it },
            onSetShowInfoDialog = { showInfoDialogItem = it },
            onSetShowOtgGuide = { showOtgGuideDialog = it },
            previewCacheManager = previewCache
        )
    }
}
