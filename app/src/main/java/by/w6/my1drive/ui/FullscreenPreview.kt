package by.w6.my1drive.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    onClose: () -> Unit,
    onShowInfo: (MediaItem) -> Unit,
    onDeleteImmediate: (MediaItem) -> Unit,
    onArchiveSingle: (MediaItem, Uri) -> Unit,
    onRestoreSingle: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit,
    isSharingPreparing: Boolean
) {
    var previewItems by remember(state.items) { mutableStateOf(state.items.toMutableList()) }
    val pagerState = rememberPagerState(
        initialPage = state.initialIndex.coerceIn(0, (previewItems.size - 1).coerceAtLeast(0)),
        pageCount = { previewItems.size }
    )

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
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val item = previewItems.getOrNull(page) ?: return@HorizontalPager
                    PagerPage(
                        item = item,
                        imageLoader = imageLoader,
                        isOtgConnected = isOtgConnected,
                        onShowInfo = onShowInfo
                    )
                }
            }

            // ─── Top bar ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
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
                    Column(modifier = Modifier.weight(1f)) {
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
                    IconButton(onClick = { onShowInfo(currentItem) }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }
                }
            }

            // ─── Bottom bar ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
                    .padding(start = 16.dp, end = 16.dp, bottom = 48.dp)
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
    onShowInfo: (MediaItem) -> Unit
) {
    if (item.isVideo) {
        VideoPage(item = item, isOtgConnected = isOtgConnected, onShowInfo = onShowInfo)
    } else {
        ImagePage(item = item, imageLoader = imageLoader, isOtgConnected = isOtgConnected, onShowInfo = onShowInfo)
    }
}

@Composable
private fun ImagePage(
    item: MediaItem,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean,
    onShowInfo: (MediaItem) -> Unit
) {
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

    var zoomScale by remember { mutableStateOf(1f) }
    var zoomOffsetX by remember { mutableStateOf(0f) }
    var zoomOffsetY by remember { mutableStateOf(0f) }

    var dragAmountY by remember { mutableStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        dragAmountY += delta
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                enabled = (zoomScale == 1f),
                onDragStarted = { dragAmountY = 0f },
                onDragStopped = { velocity ->
                    if (dragAmountY < -150f) {
                        onShowInfo(item)
                    }
                }
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown()
                    var wasDoubleTap = false

                    // Check for double tap: wait a bit for a second tap
                    val secondDown = withTimeoutOrNull(250L) {
                        awaitFirstDown(requireUnconsumed = false)
                    }
                    if (secondDown != null) {
                        // Double tap detected
                        wasDoubleTap = true
                        val tapOffset = secondDown.position
                        if (zoomScale > 1f) {
                            zoomScale = 1f
                            zoomOffsetX = 0f
                            zoomOffsetY = 0f
                        } else {
                            zoomScale = 3f
                            zoomOffsetX = (tapOffset.x * 2f).coerceIn(-1000f, 1000f)
                            zoomOffsetY = (tapOffset.y * 2f).coerceIn(-1000f, 1000f)
                        }
                        // Consume the rest of the second tap
                        val up = awaitPointerEvent()
                        up.changes.forEach { if (it.pressed) it.consume() }
                    }

                    if (!wasDoubleTap) {
                        // Track multi-touch gestures (zoom + pan)
                        var zoom = 1f
                        var pan = Offset.Zero
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop

                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
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

                                    if (zoomScale > 1f) {
                                        val maxPanX = (zoomScale - 1f) * 500f
                                        val maxPanY = (zoomScale - 1f) * 500f
                                        zoomOffsetX = (zoomOffsetX + panChange.x).coerceIn(-maxPanX, maxPanX)
                                        zoomOffsetY = (zoomOffsetY + panChange.y).coerceIn(-maxPanY, maxPanY)
                                    } else {
                                        zoomOffsetX = 0f
                                        zoomOffsetY = 0f
                                    }

                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })
                    }
                }
            }
            .graphicsLayer(
                scaleX = zoomScale,
                scaleY = zoomScale,
                translationX = zoomOffsetX,
                translationY = zoomOffsetY
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

@UnstableApi
@Composable
private fun VideoPage(
    item: MediaItem,
    isOtgConnected: Boolean,
    onShowInfo: (MediaItem) -> Unit
) {
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
            playWhenReady = true
        }
    }

    DisposableEffect(videoUri) {
        onDispose { exoPlayer.release() }
    }

    var dragAmountY by remember { mutableStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        dragAmountY += delta
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStarted = { dragAmountY = 0f },
                onDragStopped = { velocity ->
                    if (dragAmountY < -150f) {
                        onShowInfo(item)
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
                    }
                    playerView.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    addView(playerView)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private class TouchInterceptingFrameLayout(context: Context) : FrameLayout(context) {
    private var startX = 0f
    private var startY = 0f
    private var isHorizontalDrag = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                isHorizontalDrag = false
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - startX
                val dy = ev.y - startY
                // Do not intercept if touch is in bottom 20% (SeekBar area)
                if (ev.y < height * 0.8f) {
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        isHorizontalDrag = true
                        parent.requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isHorizontalDrag) {
            return false
        }
        return super.onTouchEvent(event)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
}

