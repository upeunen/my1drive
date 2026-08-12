package by.w6.my1drive.ui.components

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
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
import by.w6.my1drive.utils.OtgFolderResolver
import java.io.File

@Composable
fun OtgStorageSeparatorBar(
    isOtgConnected: Boolean,
    otgDirectoryDisplayName: String?,
    otgDirectoryUri: Uri? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val (realFreeGb, realTotalGb, progress) = remember(isOtgConnected, otgDirectoryDisplayName, otgDirectoryUri) {
        var freeGbVal = -1.0
        var totalGbVal = -1.0

        if (isOtgConnected) {
            try {
                val targetUuid = otgDirectoryUri?.let { OtgFolderResolver.extractVolumeId(it) }

                // Strategy 1: StorageManager removable volumes
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                if (sm != null) {
                    val volumes = sm.storageVolumes
                    val matchedVol = volumes.firstOrNull { vol ->
                        (targetUuid != null && vol.uuid.equals(targetUuid, ignoreCase = true)) ||
                        (vol.isRemovable && vol.state == Environment.MEDIA_MOUNTED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && matchedVol != null) {
                        val dir = matchedVol.directory
                        if (dir != null && dir.totalSpace > 0) {
                            freeGbVal = dir.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            totalGbVal = dir.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                        }
                    }
                    if (totalGbVal <= 0 && matchedVol?.uuid != null) {
                        val f = File("/storage/${matchedVol.uuid}")
                        if (f.exists() && f.totalSpace > 0) {
                            freeGbVal = f.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            totalGbVal = f.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                        }
                    }
                }

                // Strategy 2: Direct File path resolution via Extracted UUID
                if (totalGbVal <= 0 && targetUuid != null) {
                    val candidatePaths = listOf(
                        File("/storage/$targetUuid"),
                        File("/mnt/media_rw/$targetUuid")
                    )
                    for (f in candidatePaths) {
                        if (f.exists() && f.totalSpace > 0) {
                            freeGbVal = f.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            totalGbVal = f.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            break
                        }
                    }
                }

                // Strategy 3: Open PFD Descriptor on SAF tree URI
                if (totalGbVal <= 0 && otgDirectoryUri != null) {
                    try {
                        context.contentResolver.openFileDescriptor(otgDirectoryUri, "r")?.use { pfd ->
                            val stat = StatFs(pfd.fileDescriptor.toString())
                            if (stat.totalBytes > 0) {
                                freeGbVal = stat.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                                totalGbVal = stat.totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore PFD exceptions
                    }
                }

                // Strategy 4: Directory scan of /storage and /mnt/media_rw
                if (totalGbVal <= 0) {
                    val dirsToScan = listOf(File("/storage"), File("/mnt/media_rw"))
                    for (parent in dirsToScan) {
                        if (parent.exists() && parent.isDirectory) {
                            val otgMount = parent.listFiles()?.firstOrNull { f ->
                                f.isDirectory && f.name != "emulated" && f.name != "self" && f.totalSpace > 0
                            }
                            if (otgMount != null) {
                                freeGbVal = otgMount.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                                totalGbVal = otgMount.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val prog = if (totalGbVal > 0) {
            ((totalGbVal - freeGbVal) / totalGbVal).toFloat().coerceIn(0f, 1f)
        } else 0f

        Triple(freeGbVal, totalGbVal, prog)
    }

    val driveTitle = otgDirectoryDisplayName ?: stringResource(R.string.otg_archive_folder)
    val statusText = when {
        !isOtgConnected -> stringResource(R.string.drive_known_disconnected)
        realTotalGb > 0 -> stringResource(R.string.phone_storage_free_fmt, realFreeGb, realTotalGb)
        else -> stringResource(R.string.drive_known_connected)
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
                color = if (isOtgConnected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { if (isOtgConnected) progress else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = if (isOtgConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}
