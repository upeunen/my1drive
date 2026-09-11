package by.w6.my1drive.ui.components.dialogs

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.data.local.ArchiveEntity
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SelectArchiveDialog(
    archives: List<ArchiveEntity>,
    uri: Uri,
    onSelectArchive: (ArchiveEntity) -> Unit,
    onCreateNewArchive: (Uri) -> Unit,
    onDeepSearch: suspend (Set<String>) -> List<ArchiveEntity>,
    onDismiss: () -> Unit
) {
    val currentArchives = remember(archives) {
        mutableStateListOf(*archives.sortedByDescending { maxOf(it.lastConnected, it.dateCreated) }.toTypedArray())
    }
    val coroutineScope = rememberCoroutineScope()
    var isSearchingDeeper by remember { mutableStateOf(false) }
    var noNewFoundMessage by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!isSearchingDeeper) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.select_archive_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.select_archive_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentArchives, key = { it.uuid }) { archive ->
                        val fallbackName = stringResource(R.string.select_archive_fallback_name, archive.uuid.take(6))
                        val displayName = archive.name.ifBlank { fallbackName }
                        val timestamp = maxOf(archive.lastConnected, archive.dateCreated)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSearchingDeeper) { onSelectArchive(archive) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (archive.folderName.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "📁 /${archive.folderName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (timestamp > 0) {
                                        Spacer(Modifier.height(2.dp))
                                        val dateStr = remember(timestamp) {
                                            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
                                        }
                                        Text(
                                            text = "🕒 $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Кнопка глубокого поиска по диску (глубина 2)
                OutlinedButton(
                    onClick = {
                        if (!isSearchingDeeper) {
                            isSearchingDeeper = true
                            noNewFoundMessage = false
                            coroutineScope.launch {
                                val known = currentArchives.map { it.folderName }.toSet()
                                val extraFound = onDeepSearch(known)
                                val newItems = extraFound.filter { extra -> currentArchives.none { it.uuid == extra.uuid } }
                                if (newItems.isNotEmpty()) {
                                    val sorted = (currentArchives + newItems)
                                        .distinctBy { it.uuid }
                                        .sortedByDescending { maxOf(it.lastConnected, it.dateCreated) }
                                    currentArchives.clear()
                                    currentArchives.addAll(sorted)
                                } else {
                                    noNewFoundMessage = true
                                }
                                isSearchingDeeper = false
                            }
                        }
                    },
                    enabled = !isSearchingDeeper,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSearchingDeeper) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.select_archive_deep_searching),
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.select_archive_btn_deep_search),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                if (noNewFoundMessage) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.select_archive_no_new_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { onCreateNewArchive(uri) },
                    enabled = !isSearchingDeeper,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.select_archive_btn_create_new),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSearchingDeeper
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}
