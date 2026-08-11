package by.w6.my1drive.ui.components

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import java.io.File

@Composable
fun OtgStorageSeparatorBar(
    isOtgConnected: Boolean,
    otgDirectoryDisplayName: String?,
    currentArchiveSize: Long,
    isLimitActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val limitBytes = 128 * 1024 * 1024L

    val (realFreeGb, realTotalGb, archiveMb, progress) = remember(isOtgConnected, otgDirectoryDisplayName, currentArchiveSize, isLimitActive) {
        var freeGbVal = -1.0
        var totalGbVal = -1.0
        if (isOtgConnected) {
            try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                if (sm != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val vol = sm.storageVolumes.firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
                        val dir = vol?.directory
                        if (dir != null && dir.totalSpace > 0) {
                            freeGbVal = dir.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            totalGbVal = dir.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                        }
                    }
                }
                if (totalGbVal <= 0) {
                    val storageDir = File("/storage")
                    if (storageDir.exists() && storageDir.isDirectory) {
                        val otgMount = storageDir.listFiles()?.firstOrNull { f ->
                            f.isDirectory && f.name != "emulated" && f.name != "self" && f.canRead() && f.totalSpace > 0
                        }
                        if (otgMount != null) {
                            freeGbVal = otgMount.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            totalGbVal = otgMount.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore fallback to DB size
            }
        }

        val archMb = currentArchiveSize.toDouble() / (1024.0 * 1024.0)
        val prog = if (totalGbVal > 0) {
            ((totalGbVal - freeGbVal) / totalGbVal).toFloat().coerceIn(0f, 1f)
        } else if (isLimitActive) {
            (currentArchiveSize.toFloat() / limitBytes).coerceIn(0f, 1f)
        } else 0f

        Tuple4(freeGbVal, totalGbVal, archMb, prog)
    }

    val driveTitle = otgDirectoryDisplayName ?: stringResource(R.string.otg_archive_folder)
    val statusText = when {
        !isOtgConnected -> stringResource(R.string.drive_known_disconnected)
        realTotalGb > 0 -> stringResource(R.string.phone_storage_free_fmt, realFreeGb, realTotalGb)
        isLimitActive -> stringResource(R.string.archive_used_free, archiveMb, progress * 100)
        else -> {
            val sizeStr = if (archiveMb >= 1024.0) "%.2f ГБ".format(archiveMb / 1024.0) else "%.1f МБ".format(archiveMb)
            "Занято в архиве: $sizeStr"
        }
    }

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
                text = driveTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isOtgConnected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { if (isOtgConnected) progress else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = if (isOtgConnected) {
                if (isLimitActive && progress >= 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
