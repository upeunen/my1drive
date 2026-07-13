package by.w6.my1drive.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R

@Composable
fun ArchiveProgressSection(
    currentArchiveSize: Long,
    isLimitActive: Boolean
) {
    val limitBytes = 128 * 1024 * 1024L
    val progress = if (isLimitActive) {
        (currentArchiveSize.toFloat() / limitBytes).coerceIn(0f, 1f)
    } else {
        0f
    }
    val isFull = isLimitActive && currentArchiveSize >= limitBytes

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFull)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SdStorage,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isLimitActive) stringResource(R.string.archive_volume_free) else stringResource(R.string.archive_volume_pro),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))

            if (isLimitActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = if (progress >= 0.9f) {
                                        listOf(Color(0xFFE0AAFF), Color(0xFFF44336))
                                    } else {
                                        listOf(Color(0xFF8A2BE2), Color(0xFFE0AAFF))
                                    }
                                )
                            )
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            val usedMb = currentArchiveSize.toDouble() / (1024.0 * 1024.0)
            Text(
                text = if (isLimitActive) stringResource(R.string.archive_used_free, usedMb, progress * 100)
                else stringResource(R.string.archive_used_pro, usedMb),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (isFull) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.archive_limit_reached),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
