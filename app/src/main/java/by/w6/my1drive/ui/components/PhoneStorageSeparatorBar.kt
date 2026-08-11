package by.w6.my1drive.ui.components

import android.os.Environment
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    var previousArchiving by remember { mutableStateOf(isArchiving) }
    var triggerGlow by remember { mutableStateOf(false) }
    var refreshCounter by remember { mutableIntStateOf(0) }

    // Re-query disk space on items change, archiving change, or post-deletion refresh ticks
    LaunchedEffect(isArchiving) {
        if (previousArchiving && !isArchiving) {
            triggerGlow = true
            // Refresh disk space after OS finishes file deletions
            kotlinx.coroutines.delay(600)
            refreshCounter++
            kotlinx.coroutines.delay(1500)
            refreshCounter++
            kotlinx.coroutines.delay(1500)
            triggerGlow = false
        }
        previousArchiving = isArchiving
    }

    val (freeGb, totalGb, progress) = remember(mediaItemsCount, physicalArchiveSize, isArchiving, refreshCounter) {
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

    val glowAlpha by animateFloatAsState(
        targetValue = if (triggerGlow) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.phone_storage_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.phone_storage_free_fmt, freeGb, totalGb),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(2.dp))

        // 2dp Progress Line (Only this line shimmers after archiving)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            val fillModifier = if (glowAlpha > 0f) {
                val shimmerBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF00E5FF),
                        Color(0xFF8A2BE2),
                        Color(0xFF00E5FF)
                    ),
                    startX = shimmerPhase * 1000f - 500f,
                    endX = shimmerPhase * 1000f + 500f
                )
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(shimmerBrush)
            } else {
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            }
            Box(modifier = fillModifier)
        }
    }
}
