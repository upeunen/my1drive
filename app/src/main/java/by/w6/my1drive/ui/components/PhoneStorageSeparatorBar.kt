package by.w6.my1drive.ui.components

import android.os.Environment
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R

@Composable
fun PhoneStorageSeparatorBar(
    mediaItemsCount: Int = 0,
    physicalArchiveSize: Long = 0L,
    isArchiving: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Dynamic recalculation using reliable File.usableSpace / totalSpace
    val (freeGb, totalGb, progress) = remember(mediaItemsCount, physicalArchiveSize, isArchiving) {
        try {
            val storageDir = Environment.getExternalStorageDirectory()
            val totalBytes = storageDir.totalSpace
            val availableBytes = storageDir.usableSpace
            val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)

            val totalGbVal = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val freeGbVal = availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val prog = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

            Triple(freeGbVal, totalGbVal, prog)
        } catch (e: Exception) {
            Triple(0.0, 0.0, 0f)
        }
    }

    // Glow & Bounce ("скакать") animation after archiving completes
    var previousArchiving by remember { mutableStateOf(isArchiving) }
    var triggerGlow by remember { mutableStateOf(false) }

    val bounceY = remember { Animatable(0f) }
    val bounceScale = remember { Animatable(1f) }

    LaunchedEffect(isArchiving) {
        if (previousArchiving && !isArchiving) {
            triggerGlow = true
            // Play bouncy jump animation ("скакать"): 3 bounces
            repeat(3) {
                bounceY.animateTo(-8f, tween(150, easing = FastOutSlowInEasing))
                bounceScale.animateTo(1.12f, tween(150))
                bounceY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                bounceScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            kotlinx.coroutines.delay(2000)
            triggerGlow = false
        }
        previousArchiving = isArchiving
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (triggerGlow) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "glowAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerPhase"
    )

    val normalBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val glowColor1 = Color(0xFF00E5FF) // Cyan
    val glowColor2 = Color(0xFF8A2BE2) // Purple

    val barModifier = if (glowAlpha > 0f) {
        val colors = listOf(
            glowColor1.copy(alpha = 0.25f * glowAlpha),
            glowColor2.copy(alpha = 0.45f * glowAlpha),
            glowColor1.copy(alpha = 0.25f * glowAlpha)
        )
        modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = colors,
                    startX = shimmerPhase * 1000f - 500f,
                    endX = shimmerPhase * 1000f + 500f
                )
            )
    } else {
        modifier
            .fillMaxWidth()
            .background(normalBg)
    }

    Column(
        modifier = barModifier.padding(top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .graphicsLayer {
                    translationY = bounceY.value.dp.toPx()
                    scaleX = bounceScale.value
                    scaleY = bounceScale.value
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.phone_storage_title),
                style = MaterialTheme.typography.labelMedium,
                color = if (glowAlpha > 0f) glowColor1 else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (glowAlpha > 0f) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.phone_storage_free_fmt, freeGb, totalGb),
                style = MaterialTheme.typography.labelSmall,
                color = if (glowAlpha > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (glowAlpha > 0f) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .graphicsLayer {
                    scaleY = if (glowAlpha > 0f) 1.8f else 1.0f
                },
            color = if (glowAlpha > 0f) glowColor1 else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}
