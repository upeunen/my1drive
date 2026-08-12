package by.w6.my1drive.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.data.local.ArchiveEntity

@Composable
fun OtgSettingsSection(
    isOtgConnected: Boolean,
    otgDirectoryDisplayName: String?,
    isLocalFolder: Boolean,
    onSelectOtgDirectory: () -> Unit,
    knownArchives: List<ArchiveEntity>,
    onDeleteArchive: (String) -> Unit,
    activeArchiveUuid: String?,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE) }

    // OTG/USB Storage Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.otg_archive_folder),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = otgDirectoryDisplayName ?: stringResource(R.string.drive_not_selected),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isOtgConnected) stringResource(R.string.drive_known_connected)
                else stringResource(R.string.drive_known_disconnected),
                style = MaterialTheme.typography.bodySmall,
                color = if (isOtgConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            if (isLocalFolder && otgDirectoryDisplayName != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = stringResource(R.string.warning_internal_storage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSelectOtgDirectory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (otgDirectoryDisplayName != null) stringResource(R.string.change_otg_folder)
                    else stringResource(R.string.select_otg_folder),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Multi-Archive settings card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            var showOffline by remember {
                mutableStateOf(prefs.getBoolean("show_offline_archives", false))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_multi_archive_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.setting_show_offline_files),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = showOffline,
                    onCheckedChange = { checked ->
                        showOffline = checked
                        prefs.edit().putBoolean("show_offline_archives", checked).apply()
                        onRefresh()
                    }
                )
            }

            if (knownArchives.isNotEmpty() && showOffline) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.setting_connected_offline_drives),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                var archiveToDelete by remember { mutableStateOf<ArchiveEntity?>(null) }

                if (archiveToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { archiveToDelete = null },
                        title = { Text(stringResource(R.string.dialog_delete_archive_title)) },
                        text = {
                            val arcName = archiveToDelete?.name?.ifBlank { archiveToDelete?.folderName?.ifBlank { archiveToDelete?.uuid?.take(8) } } ?: ""
                            Text(stringResource(R.string.dialog_delete_archive_msg, arcName))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    archiveToDelete?.uuid?.let { onDeleteArchive(it) }
                                    archiveToDelete = null
                                }
                            ) {
                                Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { archiveToDelete = null }) {
                                Text(stringResource(R.string.btn_cancel))
                            }
                        }
                    )
                }

                knownArchives.forEachIndexed { idx, archive ->
                    val stripe = archiveStripeColor(archive.uuid)
                    val fallbackName = stringResource(R.string.archive_name_fallback, archive.uuid.take(6))
                    val displayName = archive.name.ifBlank { archive.folderName.ifBlank { fallbackName } }
                    val subtitle = if (archive.folderName.isNotEmpty()) stringResource(R.string.subtitle_folder, archive.folderName) else stringResource(R.string.subtitle_uuid, archive.uuid.take(12))
                    val isCurrentConnected = isOtgConnected && archive.uuid == activeArchiveUuid

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(stripe),
                            contentAlignment = Alignment.Center
                        ) {
                            val hashVal = Math.abs(archive.uuid.hashCode())
                            val icon = when (hashVal % 3) {
                                0 -> Icons.Default.SdStorage
                                1 -> Icons.Default.Usb
                                else -> Icons.Default.Save
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isCurrentConnected) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        } else {
                            IconButton(
                                onClick = { archiveToDelete = archive },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.content_desc_delete_archive),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Function that generates a consistent color based on UUID (same as in SettingsTab.kt currently)
fun archiveStripeColor(uuid: String): Color {
    val hue = Math.abs(uuid.hashCode() % 360).toFloat()
    return Color.hsv(hue, 0.6f, 0.8f)
}
