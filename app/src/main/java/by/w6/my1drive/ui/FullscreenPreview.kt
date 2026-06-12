package by.w6.my1drive.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
    onClose: () -> Unit,
    onShowInfo: () -> Unit
) {
    BackHandler(onBack = onClose)

    var dragOffsetY by remember { mutableStateOf(0f) }
    var isClosing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

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

    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3000)
        controlsVisible = false
    }

    var zoomScale by remember { mutableStateOf(1f) }
    var zoomOffsetX by remember { mutableStateOf(0f) }
    var zoomOffsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
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
    ) {
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

        AnimatedVisibility(
            visible = controlsVisible || dragOffsetY > 20f,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .statusBarsPadding(),
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
                    IconButton(onClick = onShowInfo) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }
                }

                // Bottom bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                        .padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(
                        onClick = {
                            isClosing = true
                            scope.launch {
                                delay(150)
                                onClose()
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_close)
                        )
                    }

                    val hintText = if (!item.isVideo) "Swipe down to close \u00b7 Pinch to zoom" else "Swipe down to close"
                    Text(
                        text = hintText,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    FloatingActionButton(
                        onClick = onShowInfo,
                        modifier = Modifier.size(48.dp),
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info"
                        )
                    }
                }
            }
        }

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
}
