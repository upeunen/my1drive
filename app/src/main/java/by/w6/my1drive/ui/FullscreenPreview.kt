package by.w6.my1drive.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import android.content.Context
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@Composable
fun FullscreenPreview(
    state: FullscreenState,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean,
    otgDirectoryUri: Uri?,
    activeArchiveUuid: String? = null,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {},
    onClose: () -> Unit,
    onShowInfo: (MediaItem) -> Unit,
    onDeleteImmediate: (MediaItem) -> Unit,
    onArchiveSingle: (MediaItem, Uri) -> Unit,
    onRestoreSingle: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit,
    isSharingPreparing: Boolean,
    isArchiving: Boolean
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showOverlays by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) break
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }

    var previewItems by remember(state.items) { mutableStateOf(state.items.toMutableList()) }
    val pagerState = rememberPagerState(
        initialPage = state.initialIndex.coerceIn(0, (previewItems.size - 1).coerceAtLeast(0)),
        pageCount = { previewItems.size }
    )

    // Auto-hide overlays after 3.5 seconds of inactivity (only if not pinned)
    androidx.compose.runtime.LaunchedEffect(showOverlays, pagerState.currentPage, isPinned) {
        if (showOverlays && !isPinned) {
            delay(3500)
            showOverlays = false
        }
    }

    // Reset overlays on swipe (only if not pinned)
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        if (!isPinned) {
            showOverlays = false
        }
    }

    // Immersive Mode (System UI toggling)
    androidx.compose.runtime.LaunchedEffect(showOverlays) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (showOverlays) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Restore System UI when closed
    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var isNavigatingBack by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val currentItem = if (previewItems.isNotEmpty()) {
        val safeIndex = pagerState.currentPage.coerceIn(0, previewItems.size - 1)
        previewItems[safeIndex]
    } else {
        return
    }

    BackHandler {
        if (!isNavigatingBack) {
            isNavigatingBack = true
            scope.launch { delay(300); onClose() }
        }
    }

    fun deleteCurrentItem(item: MediaItem) {
        val currentIndex = pagerState.currentPage
        if (previewItems.size <= 1) {
            onDeleteImmediate(item)
            onClose()
        } else {
            val targetPage = if (currentIndex < previewItems.size - 1) {
                currentIndex
            } else {
                currentIndex - 1
            }
            scope.launch {
                onDeleteImmediate(item)
                val newList = previewItems.toMutableList().apply { removeAt(currentIndex) }
                previewItems = newList
                pagerState.scrollToPage(targetPage.coerceIn(0, newList.size - 1))
            }
        }
    }

    fun archiveCurrentItem(item: MediaItem, uri: Uri) {
        val currentIndex = pagerState.currentPage
        if (previewItems.size <= 1) {
            onArchiveSingle(item, uri)
            onClose()
        } else {
            val targetPage = if (currentIndex < previewItems.size - 1) {
                currentIndex
            } else {
                currentIndex - 1
            }
            scope.launch {
                onArchiveSingle(item, uri)
                val newList = previewItems.toMutableList().apply { removeAt(currentIndex) }
                previewItems = newList
                pagerState.scrollToPage(targetPage.coerceIn(0, newList.size - 1))
            }
        }
    }

    fun restoreCurrentItem(item: MediaItem) {
        val currentIndex = pagerState.currentPage
        if (previewItems.size <= 1) {
            onRestoreSingle(item)
            onClose()
        } else {
            val targetPage = if (currentIndex < previewItems.size - 1) {
                currentIndex
            } else {
                currentIndex - 1
            }
            scope.launch {
                onRestoreSingle(item)
                val newList = previewItems.toMutableList().apply { removeAt(currentIndex) }
                previewItems = newList
                pagerState.scrollToPage(targetPage.coerceIn(0, newList.size - 1))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!isNavigatingBack) {
            // ─── Content ───
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> previewItems.getOrNull(page)?.id ?: page }
                ) { page ->
                    val item = previewItems.getOrNull(page) ?: return@HorizontalPager
                    PagerPage(
                        item = item,
                        imageLoader = imageLoader,
                        isOtgConnected = if (item.status == MediaStatus.ARCHIVED_OTG) (isOtgConnected && item.archiveUuid == activeArchiveUuid) else isOtgConnected,
                        isActive = (pagerState.currentPage == page),
                        isSelected = selectedIds.contains(item.id),
                        showOverlays = showOverlays,
                        pagerState = pagerState,
                        onShowInfo = onShowInfo,
                        onClose = onClose,
                        onTap = {
                            if (!isPinned) {
                                showOverlays = !showOverlays
                            }
                        },
                        onZoomScaleChanged = { zoomed ->
                            isZoomed = zoomed
                            if (zoomed) {
                                showOverlays = false
                            }
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleSelection(item.id)
                        }
                    )
                }
            }

            // ─── Top bar ───
            AnimatedVisibility(
                visible = showOverlays,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(start = if (isLandscape) 180.dp else 0.dp)) {
                            Text(
                                text = when (state.sourceTab) {
                                    SourceTab.PHOTOS -> stringResource(R.string.preview_source_device)
                                    SourceTab.ARCHIVE -> stringResource(R.string.tab_archive)
                                },
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = currentItem.displayName,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val isSelected = selectedIds.contains(currentItem.id)
                        IconButton(onClick = { onToggleSelection(currentItem.id) }) {
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                                contentDescription = "Toggle Selection",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        IconButton(
                            onClick = { onShare(currentItem) },
                            enabled = currentItem.status == MediaStatus.ON_DEVICE || (isOtgConnected && currentItem.otgUri != null)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = if (currentItem.status == MediaStatus.ON_DEVICE || (isOtgConnected && currentItem.otgUri != null)) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(onClick = { isPinned = !isPinned }) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin overlays",
                                tint = if (isPinned) Color(0xFF64B5F6) else Color.White
                            )
                        }
                        IconButton(onClick = { onShowInfo(currentItem) }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                        }
                        if (isArchiving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).padding(end = 8.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }

            if (isLandscape) {
                // ─── Left side bar (Landscape) ───
                AnimatedVisibility(
                    visible = showOverlays,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(180.dp)
                        .align(Alignment.CenterStart)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                                )
                            )
                            .padding(start = 16.dp, end = 16.dp, top = 80.dp, bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val hintText = if (!currentItem.isVideo) {
                                stringResource(R.string.preview_hint_swipe) + "\n•\n" + stringResource(R.string.preview_hint_zoom)
                            } else {
                                stringResource(R.string.preview_hint_swipe)
                            }
                            Text(
                                text = hintText,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { deleteCurrentItem(currentItem) },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Удалить", fontSize = 13.sp, maxLines = 1, softWrap = false)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (currentItem.status == MediaStatus.ON_DEVICE) {
                                Button(
                                    onClick = {
                                        otgDirectoryUri?.let { uri ->
                                            archiveCurrentItem(currentItem, uri)
                                        }
                                    },
                                    enabled = isOtgConnected && otgDirectoryUri != null,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("В архив", fontSize = 13.sp, maxLines = 1, softWrap = false)
                                }
                            } else {
                                Button(
                                    onClick = { restoreCurrentItem(currentItem) },
                                    enabled = isOtgConnected,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Вернуть", fontSize = 13.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            } else {
                // ─── Bottom bar (Portrait) ───
                AnimatedVisibility(
                    visible = showOverlays,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                            .padding(start = 16.dp, end = 16.dp, bottom = if (currentItem.isVideo) 140.dp else 48.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val hintText = if (!currentItem.isVideo) {
                                stringResource(R.string.preview_hint_swipe) + " • " + stringResource(R.string.preview_hint_zoom)
                            } else {
                                stringResource(R.string.preview_hint_swipe)
                            }
                            Text(
                                text = hintText,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { deleteCurrentItem(currentItem) },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.preview_delete), fontSize = 13.sp)
                                }

                                if (currentItem.status == MediaStatus.ON_DEVICE) {
                                    Button(
                                        onClick = {
                                            otgDirectoryUri?.let { uri ->
                                                archiveCurrentItem(currentItem, uri)
                                            }
                                        },
                                        enabled = isOtgConnected && otgDirectoryUri != null,
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.15f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(stringResource(R.string.preview_archive), fontSize = 13.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { restoreCurrentItem(currentItem) },
                                        enabled = isOtgConnected,
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.15f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(stringResource(R.string.preview_restore), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Floating Mini Selection Badge ───
            val currentItemIsSelected = selectedIds.contains(currentItem.id)
            AnimatedVisibility(
                visible = currentItemIsSelected && !showOverlays,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.White, shape = androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ─── Navigating back indicator ───
        }

        if (isNavigatingBack || isSharingPreparing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = true) { },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    if (isSharingPreparing) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Подготовка файла...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PagerPage(
    item: MediaItem,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean,
    isActive: Boolean,
    isSelected: Boolean,
    showOverlays: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onShowInfo: (MediaItem) -> Unit,
    onClose: () -> Unit,
    onTap: () -> Unit,
    onZoomScaleChanged: (Boolean) -> Unit,
    onLongPress: () -> Unit
) {
    if (item.isVideo) {
        VideoPage(
            item = item,
            isOtgConnected = isOtgConnected,
            isActive = isActive,
            isSelected = isSelected,
            showOverlays = showOverlays,
            onShowInfo = onShowInfo,
            onClose = onClose,
            onTap = onTap,
            onLongPress = onLongPress
        )
    } else {
        ImagePage(
            item = item,
            imageLoader = imageLoader,
            isOtgConnected = isOtgConnected,
            isSelected = isSelected,
            pagerState = pagerState,
            onShowInfo = onShowInfo,
            onClose = onClose,
            onTap = onTap,
            onZoomScaleChanged = onZoomScaleChanged,
            onLongPress = onLongPress
        )
    }
}

@Composable
private fun ImagePage(
    item: MediaItem,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean,
    isSelected: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onShowInfo: (MediaItem) -> Unit,
    onClose: () -> Unit,
    onTap: () -> Unit,
    onZoomScaleChanged: (Boolean) -> Unit,
    onLongPress: () -> Unit
) {
    val selectionScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "selectionScale"
    )
    val imageUri = if (item.status == MediaStatus.ARCHIVED_OTG) {
        if (isOtgConnected && item.otgUri != null) {
            Uri.parse(item.otgUri)
        } else if (item.thumbnailPath != null) {
            Uri.fromFile(File(item.thumbnailPath))
        } else {
            Uri.EMPTY
        }
    } else {
        item.uri
    }

    var zoomScale by remember(item.id) { mutableStateOf(1f) }
    var zoomOffsetX by remember(item.id) { mutableStateOf(0f) }
    var zoomOffsetY by remember(item.id) { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val swipeOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var tapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    androidx.compose.runtime.LaunchedEffect(zoomScale) {
        onZoomScaleChanged(zoomScale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .graphicsLayer(
                translationY = swipeOffsetY.value,
                scaleX = if (swipeOffsetY.value > 0f) (1f - (swipeOffsetY.value / 1500f).coerceIn(0f, 0.3f)) else 1f,
                scaleY = if (swipeOffsetY.value > 0f) (1f - (swipeOffsetY.value / 1500f).coerceIn(0f, 0.3f)) else 1f,
                alpha = if (swipeOffsetY.value > 0f) (1f - (swipeOffsetY.value / 1200f)).coerceIn(0.1f, 1f) else 1f,
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown()
                    val tapPosition = firstDown.position
                    val tapTime = System.currentTimeMillis()

                    var longPressTriggered = false
                    val longPressJob = scope.launch {
                        delay(500)
                        longPressTriggered = true
                        onLongPress()
                    }

                    var zoom = 1f
                    var pan = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop

                    // For tracking single-finger vertical swipe when scale is 1f
                    var swipeDirectionDetected = false
                    var isVerticalSwipe = false
                    var accumulatedDragY = 0f
                    var accumulatedDragX = 0f
                    var isMultitouchHappened = false

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pointerCount = event.changes.count { it.pressed }
                            val totalDragX = event.changes.firstOrNull()?.let { abs(it.position.x - tapPosition.x) } ?: 0f
                            val totalDragY = event.changes.firstOrNull()?.let { abs(it.position.y - tapPosition.y) } ?: 0f
                            if (pointerCount > 1 || totalDragX > touchSlop || totalDragY > touchSlop) {
                                longPressJob.cancel()
                            }

                            if (pointerCount > 1) {
                                isMultitouchHappened = true
                                isVerticalSwipe = false
                                scope.launch { swipeOffsetY.snapTo(0f) }
                            }

                            if (zoomScale == 1f && !isMultitouchHappened) {
                                // Handle single-finger swipe/scroll detection
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    val positionChange = change.position - change.previousPosition
                                    accumulatedDragY += positionChange.y
                                    accumulatedDragX += positionChange.x

                                    if (!swipeDirectionDetected) {
                                        if (abs(accumulatedDragY) > touchSlop || abs(accumulatedDragX) > touchSlop) {
                                            swipeDirectionDetected = true
                                            if (abs(accumulatedDragY) > abs(accumulatedDragX)) {
                                                isVerticalSwipe = true
                                            }
                                        }
                                    }

                                    if (isVerticalSwipe) {
                                        change.consume()
                                        scope.launch {
                                            val delta = if (positionChange.y < 0) positionChange.y * 0.7f else positionChange.y
                                            swipeOffsetY.snapTo(swipeOffsetY.value + delta)
                                        }
                                    }
                                }
                            } else {
                                // Track multi-touch gestures (zoom + pan)
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (!pastTouchSlop) {
                                    zoom *= zoomChange
                                    pan += panChange
                                    val panMotion = pan.getDistance()
                                    val zoomMotion = abs(1 - zoom) * event.calculateCentroidSize(useCurrent = false)
                                    if (event.changes.size > 1 || zoomScale > 1f) {
                                        if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                            pastTouchSlop = true
                                        }
                                    }
                                }

                                if (pastTouchSlop) {
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val newScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
                                    if (newScale != zoomScale) {
                                        val scaleChange = newScale / zoomScale
                                        zoomOffsetX = centroid.x - scaleChange * (centroid.x - zoomOffsetX)
                                        zoomOffsetY = centroid.y - scaleChange * (centroid.y - zoomOffsetY)
                                    }
                                    zoomScale = newScale

                                    val w = if (containerSize.width > 0) containerSize.width.toFloat() else 1080f
                                    val h = if (containerSize.height > 0) containerSize.height.toFloat() else 1920f

                                    if (zoomScale > 1f) {
                                        val maxPanX = (zoomScale - 1f) * w
                                        val maxPanY = (zoomScale - 1f) * h
                                        val targetOffsetX = zoomOffsetX + panChange.x
                                        val excessX = when {
                                            targetOffsetX < -maxPanX -> targetOffsetX - (-maxPanX)
                                            targetOffsetX > 0f -> targetOffsetX
                                            else -> 0f
                                        }
                                        zoomOffsetX = targetOffsetX.coerceIn(-maxPanX, 0f)
                                        zoomOffsetY = (zoomOffsetY + panChange.y).coerceIn(-maxPanY, 0f)

                                        if (excessX != 0f) {
                                            pagerState.dispatchRawDelta(-excessX)
                                        }
                                    } else {
                                        zoomOffsetX = 0f
                                        zoomOffsetY = 0f
                                    }

                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            }
                        }
                    } while (!canceled && event.changes.any { it.pressed })

                    // When gesture ends:
                    longPressJob.cancel()
                    if (zoomScale > 1f && pagerState.currentPageOffsetFraction != 0f) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage)
                        }
                    }
                    if (isVerticalSwipe && !isMultitouchHappened && zoomScale == 1f) {
                        val threshold = 350f
                        if (swipeOffsetY.value < -threshold) {
                            // Swipe up -> show info
                            onShowInfo(item)
                            scope.launch { swipeOffsetY.animateTo(0f) }
                        } else if (swipeOffsetY.value > threshold) {
                            // Swipe down -> close
                            onClose()
                        } else {
                            // Snap back smoothly
                            scope.launch { swipeOffsetY.animateTo(0f) }
                        }
                    } else if (!longPressTriggered) {
                        // Snap back if multitouch happened
                        scope.launch { swipeOffsetY.animateTo(0f) }
                        if (!swipeDirectionDetected && !isMultitouchHappened) {
                            val diffTime = tapTime - lastTapTime
                            val diffPos = (tapPosition - lastTapPosition).getDistance()
                            val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

                            if (diffTime < doubleTapTimeout && diffPos < 100f) {
                                tapJob?.cancel()
                                lastTapTime = 0L

                                if (zoomScale > 1f) {
                                    zoomScale = 1f
                                    zoomOffsetX = 0f
                                    zoomOffsetY = 0f
                                } else {
                                    zoomScale = 3f
                                    val w = if (containerSize.width > 0) containerSize.width.toFloat() else 1080f
                                    val h = if (containerSize.height > 0) containerSize.height.toFloat() else 1920f
                                    val targetX = (w / 2f) - 3f * tapPosition.x
                                    val targetY = (h / 2f) - 3f * tapPosition.y
                                    zoomOffsetX = targetX.coerceIn(-2f * w, 0f)
                                    zoomOffsetY = targetY.coerceIn(-2f * h, 0f)
                                }
                            } else {
                                lastTapTime = tapTime
                                lastTapPosition = tapPosition
                                tapJob?.cancel()
                                tapJob = scope.launch {
                                    delay(doubleTapTimeout)
                                    onTap()
                                }
                            }
                        }
                    } else {
                        // Just snap back swipe Offset if long press was triggered
                        scope.launch { swipeOffsetY.animateTo(0f) }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale * selectionScale,
                    scaleY = zoomScale * selectionScale,
                    translationX = zoomOffsetX,
                    translationY = zoomOffsetY,
                    transformOrigin = TransformOrigin(0f, 0f)
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUri,
                imageLoader = imageLoader,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@UnstableApi
@Composable
private fun VideoPage(
    item: MediaItem,
    isOtgConnected: Boolean,
    isActive: Boolean,
    isSelected: Boolean,
    showOverlays: Boolean,
    onShowInfo: (MediaItem) -> Unit,
    onClose: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val selectionScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "selectionScale"
    )
    val isOffline = item.status == MediaStatus.ARCHIVED_OTG && !isOtgConnected
    if (isOffline) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Видео недоступно (OTG отключен)",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val videoUri = if (item.status == MediaStatus.ARCHIVED_OTG && item.otgUri != null) {
        Uri.parse(item.otgUri)
    } else {
        item.uri
    }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = isActive
        }
    }

    androidx.compose.runtime.LaunchedEffect(isActive, videoUri) {
        exoPlayer.playWhenReady = isActive
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isActive) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(videoUri) {
        onDispose { exoPlayer.release() }
    }

    val swipeOffsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                translationY = swipeOffsetY.value,
                scaleX = (if (swipeOffsetY.value > 0f) (1f - (swipeOffsetY.value / 1500f).coerceIn(0f, 0.3f)) else 1f) * selectionScale,
                scaleY = (if (swipeOffsetY.value > 0f) (1f - (swipeOffsetY.value / 1500f).coerceIn(0f, 0.3f)) else 1f) * selectionScale,
                alpha = if (swipeOffsetY.value > 0f) (1f - (swipeOffsetY.value / 1200f)).coerceIn(0.1f, 1f) else 1f
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onTap()
                    },
                    onLongPress = {
                        onLongPress()
                    }
                )
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch {
                        swipeOffsetY.snapTo(swipeOffsetY.value + delta)
                    }
                },
                orientation = Orientation.Vertical,
                onDragStarted = { },
                onDragStopped = { velocity ->
                    val threshold = 350f
                    if (swipeOffsetY.value < -threshold) {
                        onShowInfo(item)
                        scope.launch { swipeOffsetY.animateTo(0f) }
                    } else if (swipeOffsetY.value > threshold) {
                        onClose()
                    } else {
                        scope.launch { swipeOffsetY.animateTo(0f) }
                    }
                }
            )
    ) {
        AndroidView(
            factory = { ctx ->
                TouchInterceptingFrameLayout(ctx).apply {
                    val playerView = PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        controllerAutoShow = false
                        controllerHideOnTouch = false
                        controllerShowTimeoutMs = 0
                        hideController()
                    }
                    playerView.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    addView(playerView)
                }
            },
            update = { frameLayout ->
                val playerView = frameLayout.getChildAt(0) as PlayerView
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                }
                if (showOverlays) {
                    if (!playerView.isControllerFullyVisible) {
                        playerView.showController()
                    }
                } else {
                    playerView.hideController()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private class TouchInterceptingFrameLayout(context: Context) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.y <= height * 0.8f) {
            return false // Let Compose handle it
        }
        return super.dispatchTouchEvent(ev)
    }
}

