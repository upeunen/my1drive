package by.w6.my1drive.ui.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.data.local.ArchiveEntity
import by.w6.my1drive.utils.FormatterUtils
import by.w6.my1drive.utils.VpsConnectionManager

@Composable
fun ProSettingsSection(
    showCopyWithoutDelete: Boolean,
    onToggleCopyWithoutDelete: (Boolean) -> Unit,
    cacheFilesCount: Int,
    cacheSize: Long,
    onClearCache: () -> Unit,
    missingThumbnailsCount: Int,
    isSyncingThumbnails: Boolean,
    syncThumbnailsProgress: Pair<Int, Int>,
    onSyncThumbnails: () -> Unit,
    onCancelSyncThumbnails: () -> Unit,
    knownArchives: List<ArchiveEntity>,
    onDeleteArchive: (String) -> Unit,
    activeArchiveUuid: String?,
    isOtgConnected: Boolean,
    onRefresh: () -> Unit,
    vpsManager: VpsConnectionManager?,
    onShowDebugLogs: () -> Unit,
    onCleanOrphans: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_clear_cache_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_clear_cache_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearCache()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.dialog_clear_cache_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Clickable Accordion Trigger)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pro_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.pro_settings_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))

                    // 1. Copy without delete option switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.setting_copy_without_delete_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.setting_copy_without_delete_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = showCopyWithoutDelete,
                            onCheckedChange = onToggleCopyWithoutDelete
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))

                    // 2. Thumbnail Cache Card & Clear Cache Button
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.LocalPolice,
                                                contentDescription = null,
                                                modifier = Modifier.size(17.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(10.dp))

                                    Column {
                                        Text(
                                            text = stringResource(R.string.dashboard_cache_title),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${stringResource(R.string.files_count, cacheFilesCount)} (${FormatterUtils.formatFileSize(cacheSize)})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                FilledTonalButton(
                                    onClick = { showClearConfirmDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.dashboard_quick_clear),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Dedicated Thumbnail Sync section if missing or syncing
                            if (missingThumbnailsCount > 0 || isSyncingThumbnails) {
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                Spacer(Modifier.height(10.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.sync_thumbnails_desc, missingThumbnailsCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSyncingThumbnails) {
                                    Spacer(Modifier.height(8.dp))
                                    val (cur, total) = syncThumbnailsProgress
                                    val progressFloat = if (total > 0) cur.toFloat() / total.toFloat() else 0f
                                    LinearProgressIndicator(
                                        progress = { progressFloat },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                    Spacer(Modifier.height(6.dp))
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
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onSyncThumbnails,
                                        enabled = isOtgConnected,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.sync_thumbnails_btn), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))

                    // 3. Multi-Archive Switch & Known Archives List
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
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (archive.folderName.isNotBlank()) {
                                            Text(
                                                text = "📁 /${archive.folderName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "ID: ${archive.uuid.take(8)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    if (isCurrentConnected) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.status_active),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF2E7D32),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { archiveToDelete = archive }
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

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))

                    // 4. VPS Settings Section
                    VpsSettingsSection(vpsManager = vpsManager)

                    Spacer(Modifier.height(12.dp))

                    // 5. Maintenance & Debug Section
                    MaintenanceAndDebugSection(
                        onShowDebugLogs = onShowDebugLogs,
                        onCleanOrphans = onCleanOrphans
                    )
                }
            }
        }
    }
}
