package by.w6.my1drive.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.ui.components.ExoPlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FullscreenPreview(
    item: MediaItem,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean,
    otgDirectoryUri: android.net.Uri?,
    deferredDeleteIds: Set<String>,
    onClose: () -> Unit,
    onShowInfo: () -> Unit,
    onToggleDeferredDelete: () -> Unit,
    onArchiveSingle: () -> Unit,
    onRestoreSingle: () -> Unit
) {
    var isNavigatingBack by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var isClosing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val isMarkedForDelete = item.id in deferredDeleteIds

    val handleClose: () -> Unit = {
        if (!isNavigatingBack && !isClosing) {
            isNavigatingBack = true
            scope.launch {
                delay(300)
                onClose()
            }
        }
    }

    BackHandler(onBack = handleClose)

    val scaleFactor by animateFloatAsState(
        targetValue = if (isClosing) 0.3f else (1f - (dragOffsetY / 800f).coerceIn(0f, 0.5f)),
        label = "scale"
    )
    val alphaFactor by animateFloatAsState(
        targetValue = if (isClosing) 0f else (1f - (dragOffsetY / 600f).coerceIn(0f, 0.6f)),
        label = "alpha"
    )
    val translationY by animateFloatAsState(
        targetValue = if (isClosing) 300f else dragOffsetY,
        label = "translateY"
    )

    var zoomScale by remember { mutableStateOf(1f) }
    var zoomOffsetX by remember { mutableStateOf(0f) }
    var zoomOffsetY by remember { mutableStateOf(0f) }

    val dragModifier = if (!isNavigatingBack) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { dragOffsetY = 0f; isClosing = false },
                onDragEnd = {
                    if (dragOffsetY > 120f) {
                        isClosing = true
                        scope.launch {
                            delay(150)
                            onClose()
                        }
                    } else {
                        dragOffsetY = 0f
                    }
                },
                onDragCancel = { dragOffsetY = 0f },
                onVerticalDrag = { _, dragAmount ->
                    dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                }
            )
        }
    } else {
        Modifier.clickable(enabled = true) { /* блокируем взаимодействие */ }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(dragModifier)
    ) {
        if (!isNavigatingBack) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scaleFactor)
                    .graphicsLayer { this.alpha = alphaFactor }
                    .offset(y = translationY.dp)
            ) {
                if (item.isVideo) {
                    val videoUri = if (item.status == MediaStatus.ARCHIVED_OTG && item.otgUri != null) {
                        Uri.parse(item.otgUri)
                    } else {
                        item.uri
                    }
                    ExoPlayerView(videoUri = videoUri)
                } else {
                    val imageUri = if (item.status == MediaStatus.ARCHIVED_OTG && item.thumbnailPath != null) {
                        Uri.fromFile(File(item.thumbnailPath))
                    } else {
                        item.uri
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = zoomOffsetX,
                                translationY = zoomOffsetY
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                    if (zoomScale > 1f) {
                                        zoomOffsetX = (zoomOffsetX + pan.x).coerceIn(-1000f, 1000f)
                                        zoomOffsetY = (zoomOffsetY + pan.y).coerceIn(-1000f, 1000f)
                                    } else {
                                        zoomOffsetX = 0f
                                        zoomOffsetY = 0f
                                    }
                                }
                            },
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

            // ─── Top bar: name + info ───
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
                    Text(
                        text = item.displayName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isMarkedForDelete) {
                        Text(
                            text = stringResource(R.string.preview_will_be_deleted),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = onShowInfo) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }
                }
            }

            // ─── Bottom bar: actions ───
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
                    val hintText = if (!item.isVideo) {
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
                        // Delete toggle
                        if (isMarkedForDelete) {
                            OutlinedButton(
                                onClick = onToggleDeferredDelete,
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.preview_will_be_deleted), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onToggleDeferredDelete,
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
                        }

                        // Archive / Restore button
                        if (item.status == MediaStatus.ON_DEVICE) {
                            Button(
                                onClick = onArchiveSingle,
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
                                onClick = onRestoreSingle,
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

            // Swipe-down hint
            if (dragOffsetY > 30f && !isClosing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Release to close",
                        color = Color.White.copy(alpha = (dragOffsetY / 120f).coerceIn(0.3f, 1f)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (isNavigatingBack) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = true) { /* блокируем повторные нажатия */ },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}