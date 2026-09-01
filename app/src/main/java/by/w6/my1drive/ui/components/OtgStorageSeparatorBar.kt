package by.w6.my1drive.ui.components

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.system.Os
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
    physicalArchiveSize: Long = 0L,
    isArchiving: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var previousArchiving by remember { mutableStateOf(isArchiving) }
    var triggerGlow by remember { mutableStateOf(false) }
    var refreshCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(isArchiving) {
        if (previousArchiving && !isArchiving) {
            triggerGlow = true
            kotlinx.coroutines.delay(600)
            refreshCounter++
            kotlinx.coroutines.delay(1500)
            refreshCounter++
            kotlinx.coroutines.delay(1500)
            triggerGlow = false
        }
        previousArchiving = isArchiving
    }

    val (realFreeGb, realTotalGb, progress) = remember(isOtgConnected, otgDirectoryDisplayName, otgDirectoryUri, physicalArchiveSize, isArchiving, refreshCounter) {
        var freeGbVal = -1.0
        var totalGbVal = -1.0

        if (isOtgConnected) {
            try {
                val targetUuid = otgDirectoryUri?.let { OtgFolderResolver.extractVolumeId(it) }

                // Strategy 1: SAF Document PFD + POSIX Os.fstatvfs (most reliable on Android 10+ for SAF tree/doc URIs)
                if (otgDirectoryUri != null) {
                    try {
                        val documentUri = if (DocumentsContract.isTreeUri(otgDirectoryUri)) {
                            val treeId = DocumentsContract.getTreeDocumentId(otgDirectoryUri)
                            DocumentsContract.buildDocumentUriUsingTree(otgDirectoryUri, treeId)
                        } else {
                            otgDirectoryUri
                        }

                        context.contentResolver.openFileDescriptor(documentUri, "r")?.use { pfd ->
                            try {
                                val stat = Os.fstatvfs(pfd.fileDescriptor)
                                val blockMultiplier = if (stat.f_frsize > 0L) stat.f_frsize else stat.f_bsize
                                val totalBytes = stat.f_blocks * blockMultiplier
                                val freeBytes = stat.f_bavail * blockMultiplier

                                if (totalBytes > 0L) {
                                    freeGbVal = freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                                    totalGbVal = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                                }
                            } catch (_: Exception) {
                                val statFs = StatFs("/proc/self/fd/${pfd.fd}")
                                if (statFs.totalBytes > 0L) {
                                    freeGbVal = statFs.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                                    totalGbVal = statFs.totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore PFD exceptions
                    }
                }

                // Strategy 2: Root tree document URI by UUID fallback if otgDirectoryUri root/tree failed
                if (totalGbVal <= 0 && targetUuid != null) {
                    try {
                        val rootTreeUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "$targetUuid:")
                        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, "$targetUuid:")
                        context.contentResolver.openFileDescriptor(rootDocUri, "r")?.use { pfd ->
                            val stat = Os.fstatvfs(pfd.fileDescriptor)
                            val blockMultiplier = if (stat.f_frsize > 0L) stat.f_frsize else stat.f_bsize
                            val totalBytes = stat.f_blocks * blockMultiplier
                            val freeBytes = stat.f_bavail * blockMultiplier

                            if (totalBytes > 0L) {
                                freeGbVal = freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                                totalGbVal = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Strategy 3: StorageManager removable volumes
                if (totalGbVal <= 0) {
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
                }

                // Strategy 4: Direct File path resolution via Extracted UUID
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

                // Strategy 5: Directory scan of /storage and /mnt/media_rw
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

    val glowAlpha by animateFloatAsState(
        targetValue = if (triggerGlow) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "otgGlowAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "otgShimmer")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "otgShimmerPhase"
    )

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
