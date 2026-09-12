package by.w6.my1drive.ui.components.dialogs

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import by.w6.my1drive.R
import by.w6.my1drive.data.local.ArchiveEntity
import by.w6.my1drive.utils.DiscoveredFolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Unified rich purple theme matching SetupWizard
private val PurpleBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF21103C),
        Color(0xFF16092A),
        Color(0xFF0D041A)
    )
)
private val PurpleCardBackground = Color(0xFF281548)
private val PurpleCardBorder = Color(0xFF7E57C2).copy(alpha = 0.35f)
private val PurpleButtonColor = Color(0xFF7E57C2)
private val PurpleAccentText = Color(0xFFE1D5F5)
private val PurpleSecondaryText = Color(0xFFB39DDB)
private val PurpleIconBox = Color(0xFF3B1E6B)

private enum class DialogMode {
    ARCHIVES,
    PHOTO_FOLDERS
}

@Composable
fun ArchivesAndFoldersDialog(
    archives: List<ArchiveEntity>,
    activeArchiveUuid: String? = null,
    uri: Uri? = null,
    isWaitingMount: Boolean = false,
    onSelectArchive: (ArchiveEntity) -> Unit,
    onCreateNewArchive: (String, Uri) -> Unit,
    onScanMediaFolders: (depth: Int, onResult: (List<DiscoveredFolder>) -> Unit) -> Unit,
    onAddDiscoveredFolder: (DiscoveredFolder) -> Unit,
    onSelectOtgDirectory: () -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(DialogMode.ARCHIVES) }
    var showCreateForm by remember { mutableStateOf(false) }
    var newArchiveName by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }

    // Media folders state
    var searchDepth by remember { mutableStateOf(2) }
    var isScanningMedia by remember { mutableStateOf(false) }
    var discoveredFolders by remember { mutableStateOf<List<DiscoveredFolder>?>(null) }

    fun triggerMediaScan(depth: Int) {
        isScanningMedia = true
        onScanMediaFolders(depth) { list ->
            discoveredFolders = list
            isScanningMedia = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PurpleBackgroundGradient)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(PurpleIconBox),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mode == DialogMode.ARCHIVES) Icons.Default.FolderSpecial else Icons.AutoMirrored.Filled.ManageSearch,
                            contentDescription = null,
                            tint = Color(0xFFCE93D8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (mode == DialogMode.ARCHIVES)
                                stringResource(R.string.dialog_archives_and_folders_title)
                            else
                                stringResource(R.string.dialog_discovered_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (mode == DialogMode.ARCHIVES)
                                stringResource(R.string.settings_section_archives)
                            else
                                stringResource(R.string.dialog_discovered_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = PurpleSecondaryText
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.btn_cancel),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Waiting for Mount Status Banner
                if (isWaitingMount) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF331B58)),
                        border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFFCE93D8),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.dialog_drive_waiting_mount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                // Main Content Area
                when (mode) {
                    DialogMode.ARCHIVES -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Section: My1drive Archives
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = PurpleCardBackground),
                                border = BorderStroke(1.dp, PurpleCardBorder)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.dialog_archives_section_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    if (archives.isNotEmpty()) {
                                        archives.forEach { archive ->
                                            val isActive = archive.uuid == activeArchiveUuid
                                            Card(
                                                onClick = {
                                                    if (!isOperating) {
                                                        isOperating = true
                                                        onSelectArchive(archive)
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isActive) Color(0xFF3B1E6B) else Color(0xFF331B58)
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isActive) Color(0xFF81C784) else Color(0xFF7E57C2).copy(alpha = 0.35f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isActive) Color(0xFF2E7D32).copy(alpha = 0.4f) else PurpleIconBox),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Storage,
                                                            contentDescription = null,
                                                            tint = if (isActive) Color(0xFF81C784) else Color(0xFFCE93D8),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = archive.name.ifBlank { stringResource(R.string.select_archive_fallback_name, archive.uuid.take(6)) },
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                            if (isActive) {
                                                                Spacer(Modifier.width(8.dp))
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = Color(0xFF2E7D32).copy(alpha = 0.35f)
                                                                ) {
                                                                    Text(
                                                                        text = stringResource(R.string.dialog_archives_active_badge),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = Color(0xFF81C784),
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        if (archive.folderName.isNotBlank()) {
                                                            Spacer(Modifier.height(2.dp))
                                                            Text(
                                                                text = "📁 /${archive.folderName}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = PurpleSecondaryText
                                                            )
                                                        }
                                                        val timestamp = maxOf(archive.lastConnected, archive.dateCreated)
                                                        if (timestamp > 0) {
                                                            val dateStr = remember(timestamp) {
                                                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
                                                            }
                                                            Text(
                                                                text = "🕒 $dateStr",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = PurpleSecondaryText.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = Color(0xFFCE93D8)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = stringResource(R.string.wizard_archive_not_found_title),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PurpleSecondaryText,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // Inline Create Archive Form
                                    if (!showCreateForm && archives.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = { showCreateForm = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCE93D8)),
                                            border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.45f))
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.wizard_archive_create_title),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF1D0E35), shape = RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.wizard_archive_create_title),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                if (archives.isNotEmpty()) {
                                                    TextButton(onClick = { showCreateForm = false }) {
                                                        Text(stringResource(R.string.btn_cancel), color = Color(0xFFCE93D8))
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = newArchiveName,
                                                onValueChange = { if (it.length <= 30) newArchiveName = it },
                                                placeholder = {
                                                    Text(
                                                        stringResource(R.string.wizard_archive_name_placeholder),
                                                        color = PurpleSecondaryText.copy(alpha = 0.6f)
                                                    )
                                                },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = Color(0xFFCE93D8),
                                                    unfocusedBorderColor = PurpleCardBorder,
                                                    cursorColor = Color(0xFFCE93D8),
                                                    focusedContainerColor = Color(0xFF150827),
                                                    unfocusedContainerColor = Color(0xFF150827)
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            Button(
                                                onClick = {
                                                    if (uri != null && newArchiveName.isNotBlank() && !isOperating) {
                                                        isOperating = true
                                                        onCreateNewArchive(newArchiveName.trim(), uri)
                                                    }
                                                },
                                                enabled = newArchiveName.isNotBlank() && uri != null && !isOperating,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = PurpleButtonColor,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.wizard_btn_create_archive),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Section: Photo Folders Button
                            Button(
                                onClick = {
                                    mode = DialogMode.PHOTO_FOLDERS
                                    if (discoveredFolders == null) {
                                        triggerMediaScan(searchDepth)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF331B58),
                                    contentColor = Color(0xFFE1D5F5)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.dialog_folders_section_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Section: Change Drive (SAF Picker) Button
                            OutlinedButton(
                                onClick = onSelectOtgDirectory,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCE93D8)),
                                border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.35f))
                            ) {
                                Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.dialog_btn_choose_saf_drive),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    DialogMode.PHOTO_FOLDERS -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            // Depth selection bar
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF331B58),
                                border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.dialog_discovered_depth, searchDepth),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                if (searchDepth > 1 && !isScanningMedia) {
                                                    searchDepth--
                                                    triggerMediaScan(searchDepth)
                                                }
                                            },
                                            enabled = searchDepth > 1 && !isScanningMedia,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }

                                        Text(
                                            text = searchDepth.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFCE93D8),
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                if (searchDepth < 4 && !isScanningMedia) {
                                                    searchDepth++
                                                    triggerMediaScan(searchDepth)
                                                }
                                            },
                                            enabled = searchDepth < 4 && !isScanningMedia,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            if (isScanningMedia) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFFCE93D8),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.dialog_discovered_scanning),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PurpleAccentText
                                    )
                                }
                            } else if (discoveredFolders.isNullOrEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = PurpleSecondaryText.copy(alpha = 0.6f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.dialog_discovered_empty),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.dialog_discovered_empty_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PurpleSecondaryText
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(discoveredFolders.orEmpty(), key = { it.relativePath }) { folder ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF331B58)),
                                            border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = Color(0xFFCE93D8),
                                                    modifier = Modifier.size(26.dp)
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = folder.name,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = folder.relativePath,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = PurpleSecondaryText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Button(
                                                    onClick = {
                                                        if (!isOperating) {
                                                            isOperating = true
                                                            onAddDiscoveredFolder(folder)
                                                        }
                                                    },
                                                    enabled = !isOperating,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = PurpleButtonColor,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.wizard_btn_connect_folder),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            TextButton(
                                onClick = { mode = DialogMode.ARCHIVES },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.wizard_btn_back_to_create),
                                    color = Color(0xFFCE93D8),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
