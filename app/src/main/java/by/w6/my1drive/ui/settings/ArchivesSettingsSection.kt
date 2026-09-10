package by.w6.my1drive.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.data.local.ArchiveEntity

@Composable
fun ArchivesSettingsSection(
    knownArchives: List<ArchiveEntity>,
    onDeleteArchive: (String) -> Unit,
    activeArchiveUuid: String?,
    isOtgConnected: Boolean,
    onRefresh: () -> Unit,
    onSearchOtherArchives: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE) }
    var showOffline by remember {
        mutableStateOf(prefs.getBoolean("show_offline_archives", false))
    }
    var archiveToDelete by remember { mutableStateOf<ArchiveEntity?>(null) }

    if (archiveToDelete != null) {
        AlertDialog(
            onDismissRequest = { archiveToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_archive_title)) },
            text = {
                val arcName = archiveToDelete?.name?.ifBlank {
                    archiveToDelete?.folderName?.ifBlank { archiveToDelete?.uuid?.take(8) }
                } ?: ""
                Text(stringResource(R.string.dialog_delete_archive_msg, arcName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        archiveToDelete?.uuid?.let { onDeleteArchive(it) }
                        archiveToDelete = null
                    }
                ) {
                    Text(
                        stringResource(R.string.btn_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_section_archives).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Multi-archive toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SdStorage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setting_multi_archive_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
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

                // Known archives list
                if (knownArchives.isNotEmpty() && showOffline) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.setting_connected_offline_drives),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    knownArchives.forEach { archive ->
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
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SdStorage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
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

                // Search other archives on drive
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(10.dp))

                Surface(
                    onClick = { if (isOtgConnected) onSearchOtherArchives() },
                    enabled = isOtgConnected,
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                            contentDescription = null,
                            tint = if (isOtgConnected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.search_other_archives_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOtgConnected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isOtgConnected) stringResource(R.string.search_other_archives_subtitle)
                                else stringResource(R.string.search_other_archives_connect_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
