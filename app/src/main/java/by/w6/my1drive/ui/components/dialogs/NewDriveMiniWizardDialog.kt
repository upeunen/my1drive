package by.w6.my1drive.ui.components.dialogs

import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Unified rich purple theme matching SetupWizard and ArchivesAndFoldersDialog
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

@Composable
fun NewDriveMiniWizardDialog(
    initialStep: Int = 1,
    errorMessage: String? = null,
    driveUri: Uri? = null,
    onRequestSelectOtgFolder: () -> Unit,
    onScanArchives: suspend (Uri) -> List<ArchiveEntity>,
    onSelectArchive: (ArchiveEntity, Uri) -> Unit,
    onCreateNewArchive: (String, Uri) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember(initialStep) { mutableIntStateOf(initialStep) }
    var isSuccess by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var foundArchives by remember { mutableStateOf<List<ArchiveEntity>>(emptyList()) }
    var showCreateForm by remember { mutableStateOf(false) }
    var newArchiveName by remember {
        mutableStateOf("Arhiv-${Build.MODEL.replace("\\s+".toRegex(), "_")}")
    }

    // Auto-advance to Step 2 if driveUri is provided
    LaunchedEffect(driveUri) {
        if (driveUri != null) {
            step = 2
            isScanning = true
            try {
                foundArchives = onScanArchives(driveUri)
                if (foundArchives.isEmpty()) {
                    showCreateForm = true
                }
            } catch (_: Exception) {
                foundArchives = emptyList()
                showCreateForm = true
            } finally {
                isScanning = false
            }
        }
    }

    // Auto-dismiss after 1 second on success
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            delay(1000L)
            onDismiss()
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Dialog(
        onDismissRequest = {
            if (!isSuccess) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isSuccess,
            dismissOnClickOutside = !isSuccess
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16092A)),
            border = BorderStroke(1.5.dp, PurpleCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PurpleBackgroundGradient)
                    .padding(20.dp)
            ) {
                // Close button top right
                if (!isSuccess) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.btn_close),
                            tint = PurpleSecondaryText
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = if (isSuccess) 3 else step,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "MiniWizardSteps"
                    ) { targetStep ->
                        when (targetStep) {
                            1 -> {
                                // ─── Step 1: SAF Root Access Request ───
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(PurpleIconBox),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Usb,
                                            contentDescription = null,
                                            tint = Color(0xFFCE93D8),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = stringResource(R.string.mini_wizard_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = stringResource(R.string.mini_wizard_saf_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PurpleAccentText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )

                                    if (errorMessage != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFFB71C1C).copy(alpha = 0.25f)
                                            ),
                                            border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFF8A80),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = errorMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFFFFCDD2)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Button(
                                        onClick = onRequestSelectOtgFolder,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PurpleButtonColor,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            text = stringResource(
                                                if (errorMessage != null) R.string.mini_wizard_btn_retry
                                                else R.string.mini_wizard_btn_grant
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            2 -> {
                                // ─── Step 2: Archives Search, Selection & Creation ───
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(PurpleIconBox),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderSpecial,
                                            contentDescription = null,
                                            tint = Color(0xFFCE93D8),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (isScanning) {
                                        Text(
                                            text = stringResource(R.string.mini_wizard_searching),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        CircularProgressIndicator(
                                            color = PurpleButtonColor,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    } else if (!showCreateForm && foundArchives.size == 1) {
                                        // Case 1: Exactly 1 archive found
                                        val archive = foundArchives.first()
                                        Text(
                                            text = stringResource(R.string.mini_wizard_single_found_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = PurpleCardBackground),
                                            border = BorderStroke(1.dp, PurpleCardBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(PurpleIconBox),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint = Color(0xFFCE93D8),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = archive.name.ifEmpty { archive.folderName },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = dateFormat.format(Date(archive.lastConnected.takeIf { it > 0 } ?: archive.dateCreated)),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = PurpleSecondaryText
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(18.dp))

                                        Button(
                                            onClick = {
                                                driveUri?.let { uri ->
                                                    onSelectArchive(archive, uri)
                                                    isSuccess = true
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PurpleButtonColor,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.mini_wizard_btn_connect_archive),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        TextButton(onClick = { showCreateForm = true }) {
                                            Text(
                                                text = stringResource(R.string.mini_wizard_btn_create_other),
                                                color = PurpleSecondaryText,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    } else if (!showCreateForm && foundArchives.size > 1) {
                                        // Case 2: Multiple archives found
                                        Text(
                                            text = stringResource(R.string.mini_wizard_select_archive_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            foundArchives.forEach { arch ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            driveUri?.let { uri ->
                                                                onSelectArchive(arch, uri)
                                                                isSuccess = true
                                                            }
                                                        },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = CardDefaults.cardColors(containerColor = PurpleCardBackground),
                                                    border = BorderStroke(1.dp, PurpleCardBorder)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(PurpleIconBox),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Folder,
                                                                contentDescription = null,
                                                                tint = Color(0xFFCE93D8),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = arch.name.ifEmpty { arch.folderName },
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = dateFormat.format(Date(arch.lastConnected.takeIf { it > 0 } ?: arch.dateCreated)),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = PurpleSecondaryText
                                                            )
                                                        }
                                                        Icon(
                                                            imageVector = Icons.Default.ChevronRight,
                                                            contentDescription = null,
                                                            tint = PurpleSecondaryText,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        OutlinedButton(
                                            onClick = { showCreateForm = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, PurpleCardBorder)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = PurpleAccentText,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.mini_wizard_create_new_archive),
                                                color = PurpleAccentText,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    } else {
                                        // Case 3: 0 archives or Create form active
                                        Text(
                                            text = stringResource(
                                                if (foundArchives.isEmpty()) R.string.mini_wizard_no_archives_found
                                                else R.string.mini_wizard_create_new_archive
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = stringResource(R.string.wizard_archive_create_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PurpleAccentText,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        OutlinedTextField(
                                            value = newArchiveName,
                                            onValueChange = { newArchiveName = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFCE93D8),
                                                unfocusedBorderColor = PurpleCardBorder,
                                                focusedContainerColor = PurpleCardBackground,
                                                unfocusedContainerColor = PurpleCardBackground,
                                                cursorColor = Color(0xFFCE93D8)
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(18.dp))

                                        Button(
                                            onClick = {
                                                val trimmed = newArchiveName.trim()
                                                if (trimmed.isNotEmpty() && driveUri != null) {
                                                    onCreateNewArchive(trimmed, driveUri)
                                                    isSuccess = true
                                                }
                                            },
                                            enabled = newArchiveName.isNotBlank() && driveUri != null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = PurpleButtonColor,
                                                contentColor = Color.White,
                                                disabledContainerColor = PurpleButtonColor.copy(alpha = 0.35f),
                                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.mini_wizard_btn_create),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (foundArchives.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            TextButton(onClick = { showCreateForm = false }) {
                                                Text(
                                                    text = stringResource(R.string.wizard_btn_back_to_create),
                                                    color = PurpleSecondaryText,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            3 -> {
                                // ─── Success state: checkmark animation ───
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF81C784),
                                        modifier = Modifier.size(64.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = stringResource(R.string.mini_wizard_success),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF81C784),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
