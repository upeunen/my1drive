package by.w6.my1drive.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onRefresh: () -> Unit,
    isSyncingThumbnails: Boolean = false,
    syncThumbnailsProgress: Pair<Int, Int> = Pair(0, 0),
    missingThumbnailsCount: Int = 0,
    onSyncThumbnails: () -> Unit = {},
    onCancelSyncThumbnails: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Main Storage & OTG Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Section Title with Icon Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_category_storage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.otg_archive_folder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Interactive Folder / Path Selection Widget
                Surface(
                    onClick = onSelectOtgDirectory,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Storage Type Tag (USB vs Internal)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isLocalFolder) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLocalFolder) Icons.Default.Smartphone else Icons.Default.Usb,
                                        contentDescription = null,
                                        tint = if (isLocalFolder) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isLocalFolder) stringResource(R.string.tag_internal_storage) else stringResource(R.string.tag_usb_storage),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLocalFolder) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            // Tap to change/select label + icon
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (otgDirectoryDisplayName != null) stringResource(R.string.click_to_change_folder)
                                    else stringResource(R.string.click_to_select_folder),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Folder Path display line
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (otgDirectoryDisplayName != null) "/$otgDirectoryDisplayName" else stringResource(R.string.drive_not_selected),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isOtgConnected) stringResource(R.string.drive_known_connected)
                                    else stringResource(R.string.drive_known_disconnected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOtgConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (isLocalFolder && otgDirectoryDisplayName != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.warning_internal_storage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(14.dp))

                // Multi-Archive Switch
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
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.setting_show_offline_files),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = showOffline,
                        onCheckedChange = { checked ->
                            showOffline = checked
                            prefs.edit().putBoolean("show_offline_archives", checked).apply()
                            onRefresh()
                        }
                    )
                }

                // Known Archives list
                if (knownArchives.isNotEmpty() && showOffline) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.setting_connected_offline_drives),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
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

                    knownArchives.forEach { archive ->
                        val stripe = archiveStripeColor(archive.uuid)
                        val fallbackName = stringResource(R.string.archive_name_fallback, archive.uuid.take(6))
                        val displayName = archive.name.ifBlank { archive.folderName.ifBlank { fallbackName } }
                        val isCurrentConnected = isOtgConnected && archive.uuid == activeArchiveUuid

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (archive.folderName.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = "📁 /${archive.folderName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = "ID: ${archive.uuid.take(8)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (isCurrentConnected) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                        modifier = Modifier.padding(start = 6.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.status_active),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { archiveToDelete = archive },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.content_desc_delete_archive),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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

        // Dedicated Thumbnail Sync Card if thumbnails are missing or syncing
        if (missingThumbnailsCount > 0 || isSyncingThumbnails) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.sync_thumbnails_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.sync_thumbnails_desc, missingThumbnailsCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isSyncingThumbnails) {
                        Spacer(Modifier.height(12.dp))
                        val (cur, total) = syncThumbnailsProgress
                        val progressFloat = if (total > 0) cur.toFloat() / total.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { progressFloat },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.sync_thumbnails_progress, cur, total),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onCancelSyncThumbnails) {
                                Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onSyncThumbnails,
                            enabled = isOtgConnected,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sync_thumbnails_btn))
                        }
                    }
                }
            }
        }
    }
}

fun archiveStripeColor(uuid: String): Color {
    val hue = Math.abs(uuid.hashCode() % 360).toFloat()
    return Color.hsv(hue, 0.6f, 0.8f)
}

