package by.w6.my1drive.ui
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.domain.model.getThumbnailModel
import coil.ImageLoader
import coil.compose.AsyncImage

/**
 * Clamps [ratio] into [minRatio..maxRatio], but never crop more than [maxCrop] fraction.
 * Returns Pair(displayRatio, cropFraction) where cropFraction ∈ [0, maxCrop].
 *
 * Rule: displayRatio ∈ [ratio * (1-maxCrop), ratio / (1-maxCrop)] ∩ [minRatio, maxRatio]
 */
fun clampedAspectRatio(
    ratio: Float,
    minRatio: Float = 0.33f,
    maxRatio: Float = 3.50f,
    maxCrop: Float = 0.05f
): Pair<Float, Float> {
    if (ratio <= 0f) return Pair(1f, 0f)
    // 25%-crop bounds relative to original
    val cropMin = ratio * (1f - maxCrop)   // narrowest display (crop sides)
    val cropMax = ratio / (1f - maxCrop)   // widest display (crop top/bottom)
    // Intersection with global bounds
    val lo = maxOf(minRatio, cropMin)
    val hi = minOf(maxRatio, cropMax)
    // If no valid intersection (extreme portrait/landscape), snap to nearest global bound
    val display = when {
        lo > hi -> if (ratio < minRatio) minRatio else maxRatio  // extreme photo: snap to bound
        else -> ratio.coerceIn(lo, hi)
    }
    // Compute actual crop fraction
    val cropFraction = when {
        display > ratio -> 1f - ratio / display   // cropped top/bottom
        display < ratio -> 1f - display / ratio   // cropped sides
        else -> 0f
    }.coerceIn(0f, maxCrop)
    return Pair(display, cropFraction)
}
@Composable
fun GooglePhotosGridItem(
    item: MediaItem,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean = true,
    isArchiving: Boolean = false,
    isCopied: Boolean = false,
    archiveStripeOverrideColor: Color? = null,
    cropFraction: Float = 0f,   // 0 = no crop; fraction of original hidden (up to 0.25)
    isCroppedHorizontally: Boolean = false,  // true = sides cropped (wide photo); false = top/bottom
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 0.93f else 1.0f, label = "Scale")
    val selectionBorderWidth by animateFloatAsState(targetValue = if (isSelected) 3f else 0f, label = "SelectionBorderWidth")
    val overlayAlpha by animateFloatAsState(targetValue = if (isSelected) 0.25f else 0.0f, label = "OverlayAlpha")
    val checkmarkScale by animateFloatAsState(targetValue = if (isSelected) 1.0f else 0.0f, label = "CheckmarkScale")
    // Shimmer: всегда вызывается вне условий (Rules of Composables)
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by shimmerTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val isArchivedOffline = item.status == MediaStatus.ARCHIVED_OTG && !isOtgConnected
    val hasCachedPreview = item.hasCachedPreview
    var isImageLoading by remember { mutableStateOf(false) }
    val imageModel = item.getThumbnailModel(isOtgConnected)

    Card(
        modifier = modifier
            .scale(scale)
            .border(
                width = selectionBorderWidth.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {

            if (isArchivedOffline && !hasCachedPreview) {
                // Placeholder: no cached preview and drive is not connected
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Default.PlayCircle else Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = Color(0xFF5E35B1).copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.preview_on_disk),
                            fontSize = 9.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                AsyncImage(
                    model = imageModel,
                    imageLoader = imageLoader,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    onLoading = { isImageLoading = true },
                    onSuccess = { isImageLoading = false },
                    onError = { isImageLoading = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isCopied || (item.status == MediaStatus.ARCHIVED_OTG && !isOtgConnected && !isArchiving)) 0.5f else 1.0f)
                )
                // Crop-edge fade indicator: subtle gradient showing photo is cropped
                if (cropFraction > 0.05f) {
                    val fadeAlpha = (cropFraction * 2f).coerceIn(0.10f, 0.40f)
                    val fadeColor = Color.Black.copy(alpha = fadeAlpha)
                    if (isCroppedHorizontally) {
                        // Sides are cropped (portrait photo displayed wider) — gradient left+right
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(
                                    0f to fadeColor,
                                    0.15f to Color.Transparent,
                                    0.85f to Color.Transparent,
                                    1f to fadeColor
                                )
                            )
                        )
                    } else {
                        // Top/bottom are cropped (landscape photo displayed narrower) — gradient top+bottom
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    0f to fadeColor,
                                    0.15f to Color.Transparent,
                                    0.85f to Color.Transparent,
                                    1f to fadeColor
                                )
                            )
                        )
                    }
                }
                if (isImageLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray.copy(alpha = shimmerAlpha))
                    )
                }
            }

            // Video play badge
            if (item.isVideo && !(isArchivedOffline && !hasCachedPreview)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = stringResource(R.string.cd_video),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                    )
                    item.duration?.let { durationMs ->
                        if (durationMs > 0) {
                            val totalSec = durationMs / 1000
                            val min = totalSec / 60
                            val sec = totalSec % 60
                            val durationStr = "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
                            Text(
                                text = durationStr,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            // Storage status icon removed
            // Selection badge with smooth animations
            if (overlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                )
            }
            if (checkmarkScale > 0f) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .scale(checkmarkScale)
                        .background(Color.White, CircleShape)
                )
            }

            // Archiving background preloader overlay
            if (isArchiving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }

            // Угловой мини-бэйдж архива вместо грубой полоски во всю ширину
            if (archiveStripeOverrideColor != null && item.status == MediaStatus.ARCHIVED_OTG) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .background(
                            color = if (isOtgConnected) archiveStripeOverrideColor else archiveStripeOverrideColor.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.8f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOtgConnected) Icons.Default.SdStorage else Icons.Default.UsbOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}


