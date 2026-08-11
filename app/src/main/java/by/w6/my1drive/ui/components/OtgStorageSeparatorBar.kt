package by.w6.my1drive.ui.components

import android.content.Context
import android.net.Uri
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
    otgDirectoryUri: Uri? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val (realFreeGb, realTotalGb, progress) = remember(isOtgConnected, otgDirectoryDisplayName, otgDirectoryUri) {
        var freeGbVal = -1.0
        var totalGbVal = -1.0

        if (isOtgConnected) {
            try {
                // Method 1: StorageManager removable volumes
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                if (sm != null) {
                    val vol = sm.storageVolumes.firstOrNull { 
                        (it.isRemovable || (it.uuid != null && it.uuid != "primary")) && it.state == Environment.MEDIA_MOUNTED 
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vol != null) {
                        val dir = vol.directory
                        if (dir != null && dir.totalSpace > 0) {
                            freeGbVal = dir.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            totalGbVal = dir.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                        }
                    }
                    if (totalGbVal <= 0 && vol != null && vol.uuid != null) {
                        val f = File("/storage/${vol.uuid}")
                        if (f.exists() && f.totalSpace > 0) {
                            freeGbVal = f.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            totalGbVal = f.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                        }
                    }
                }

                // Method 2: Extract UUID from otgDirectoryUri
                if (totalGbVal <= 0 && otgDirectoryUri != null) {
                    val uuid = by.w6.my1drive.utils.OtgFolderResolver.extractVolumeId(otgDirectoryUri)
                    if (uuid != null) {
                        val f = File("/storage/$uuid")
                        if (f.exists() && f.totalSpace > 0) {
                            freeGbVal = f.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            totalGbVal = f.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                        }
                    }
                }

                // Method 3: Direct scan of /storage
                if (totalGbVal <= 0) {
                    val storageDir = File("/storage")
                    if (storageDir.exists() && storageDir.isDirectory) {
                        val otgMount = storageDir.listFiles()?.firstOrNull { f ->
                            f.isDirectory && f.name != "emulated" && f.name != "self" && f.totalSpace > 0
                        }
                        if (otgMount != null) {
                            freeGbVal = otgMount.usableSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
                            totalGbVal = otgMount.totalSpace.toDouble() / (1024.0 * 1024.0 * 1024.0)
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
            color = if (isOtgConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}
