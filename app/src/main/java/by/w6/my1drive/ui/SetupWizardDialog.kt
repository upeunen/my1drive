package by.w6.my1drive.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import by.w6.my1drive.data.local.ArchiveEntity
import by.w6.my1drive.utils.DiscoveredFolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import by.w6.my1drive.R
import kotlinx.coroutines.launch

// Elegant rich purple theme palette
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetupWizardScreen(
    initialStep: Int = 0,
    uiState: GalleryUiState,
    isPhysConnected: Boolean = false,
    hasStoragePermission: Boolean = false,
    otgUri: Uri? = null,
    onDismiss: () -> Unit,
    onStartOtgRegistration: () -> Unit,
    onRequestFullAccess: () -> Unit,
    onScanArchives: suspend (Uri) -> List<ArchiveEntity> = { emptyList() },
    onSelectExistingArchive: (ArchiveEntity, Uri) -> Unit = { _, _ -> },
    onCreateNewArchive: (String, Uri) -> Unit = { _, _ -> },
    onScanMediaFolders: ((List<DiscoveredFolder>) -> Unit) -> Unit = {},
    onAddDiscoveredFolder: (DiscoveredFolder) -> Unit = {},
    onSkip: () -> Unit = onDismiss,
    onFinish: () -> Unit = onDismiss
) {
    val scope = rememberCoroutineScope()
    val startPage = when {
        initialStep >= 2 -> 2
        initialStep == 1 -> 1
        else -> 0
    }
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { 3 })

    // Auto-advance to Step 3 when OTG drive is selected from Step 2
    LaunchedEffect(otgUri) {
        if (otgUri != null && pagerState.currentPage == 1) {
            pagerState.animateScrollToPage(2)
        }
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PurpleBackgroundGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Step Indicators at top (3 steps)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { iteration ->
                    val isSelected = pagerState.currentPage == iteration
                    val color = if (isSelected)
                        Color(0xFFCE93D8)
                    else
                        Color(0xFF4A2C76)
                    val width = if (isSelected) 28.dp else 10.dp
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .height(8.dp)
                            .width(width)
                    )
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> WizardStep1Welcome(
                        uiState = uiState,
                        onStartClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        }
                    )
                    1 -> WizardStepPermissionsAndOtg(
                        isPhysConnected = isPhysConnected,
                        hasStoragePermission = hasStoragePermission,
                        otgUri = otgUri,
                        onRequestFullAccess = onRequestFullAccess,
                        onStartOtgRegistration = onStartOtgRegistration,
                        onNextToStep3 = {
                            scope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        },
                        onSkip = onSkip
                    )
                    2 -> WizardStep3ArchiveSetup(
                        otgUri = otgUri,
                        onScanArchives = onScanArchives,
                        onSelectExistingArchive = onSelectExistingArchive,
                        onCreateNewArchive = onCreateNewArchive,
                        onScanMediaFolders = onScanMediaFolders,
                        onAddDiscoveredFolder = onAddDiscoveredFolder,
                        onStartOtgRegistration = onStartOtgRegistration
                    )
                }
            }
        }
    }
}

/**
 * Backward compatible alias for SetupWizardScreen
 */
@Composable
fun SetupWizardDialog(
    initialStep: Int = 0,
    uiState: GalleryUiState,
    isPhysConnected: Boolean = false,
    hasStoragePermission: Boolean = false,
    otgUri: Uri? = null,
    onDismiss: () -> Unit,
    onStartOtgRegistration: () -> Unit,
    onRequestFullAccess: () -> Unit,
    onScanArchives: suspend (Uri) -> List<ArchiveEntity> = { emptyList() },
    onSelectExistingArchive: (ArchiveEntity, Uri) -> Unit = { _, _ -> },
    onCreateNewArchive: (String, Uri) -> Unit = { _, _ -> },
    onScanMediaFolders: ((List<DiscoveredFolder>) -> Unit) -> Unit = {},
    onAddDiscoveredFolder: (DiscoveredFolder) -> Unit = {},
    onSkip: () -> Unit = onDismiss,
    onFinish: () -> Unit = onDismiss
) {
    SetupWizardScreen(
        initialStep = initialStep,
        uiState = uiState,
        isPhysConnected = isPhysConnected,
        hasStoragePermission = hasStoragePermission,
        otgUri = otgUri,
        onDismiss = onDismiss,
        onStartOtgRegistration = onStartOtgRegistration,
        onRequestFullAccess = onRequestFullAccess,
        onScanArchives = onScanArchives,
        onSelectExistingArchive = onSelectExistingArchive,
        onCreateNewArchive = onCreateNewArchive,
        onScanMediaFolders = onScanMediaFolders,
        onAddDiscoveredFolder = onAddDiscoveredFolder,
        onSkip = onSkip,
        onFinish = onFinish
    )
}

@Composable
private fun WizardStep1Welcome(
    uiState: GalleryUiState,
    onStartClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(PurpleIconBox),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Usb,
                contentDescription = null,
                tint = Color(0xFFCE93D8),
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.welcome_title),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.welcome_msg),
            style = MaterialTheme.typography.bodyLarge,
            color = PurpleAccentText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Show Trial/Limits info
        if (!uiState.isPremiumUnlocked) {
            val trialText = if (uiState.isTrialActive) {
                stringResource(R.string.wizard_trial_active, uiState.remainingTrialDays)
            } else {
                stringResource(R.string.wizard_free_limits, uiState.maxPhotos, uiState.maxVideos)
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF331B58)
                ),
                border = BorderStroke(
                    1.dp,
                    Color(0xFFAB47BC).copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = trialText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF3E5F5),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            Spacer(modifier = Modifier.height(32.dp))
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        Button(
            onClick = onStartClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PurpleButtonColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = stringResource(R.string.welcome_btn_start),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WizardStepPermissionsAndOtg(
    isPhysConnected: Boolean,
    hasStoragePermission: Boolean,
    otgUri: Uri? = null,
    onRequestFullAccess: () -> Unit,
    onStartOtgRegistration: () -> Unit,
    onNextToStep3: () -> Unit = {},
    onSkip: () -> Unit = {}
) {
    val context = LocalContext.current

    // Dynamic video switching based on whether storage permission has been granted
    val currentVideoRes = remember(context, hasStoragePermission) {
        if (!hasStoragePermission) {
            val id = context.resources.getIdentifier("manage_media_guide", "raw", context.packageName)
            if (id == 0) context.resources.getIdentifier("instr", "raw", context.packageName) else id
        } else {
            context.resources.getIdentifier("otg_guide", "raw", context.packageName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video guide: 1:1 aspect ratio, compact and centered so both steps fit comfortably on screen
        if (currentVideoRes != 0) {
            Card(
                modifier = Modifier
                    .sizeIn(maxWidth = 220.dp, maxHeight = 220.dp)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF190B2E)
                ),
                border = BorderStroke(1.dp, Color(0xFF673AB7).copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                VideoGuidePlayer(
                    rawResourceId = currentVideoRes,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Sequential Step 1: Media Files Permission
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = PurpleCardBackground
            ),
            border = BorderStroke(
                1.dp,
                if (hasStoragePermission) Color(0xFF4CAF50).copy(alpha = 0.6f)
                else PurpleCardBorder
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasStoragePermission) Icons.Default.CheckCircle else Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (hasStoragePermission) Color(0xFF81C784) else Color(0xFFCE93D8),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.wizard_step_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!hasStoragePermission) {
                    Text(
                        text = stringResource(R.string.local_folder_dialog_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PurpleAccentText
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRequestFullAccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleButtonColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.local_folder_dialog_full_access),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wizard_access_granted),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81C784)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sequential Step 2: Removable Drive SAF Access & Archive Discovery
        val isStep2Enabled = hasStoragePermission
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isStep2Enabled) 1f else 0.5f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = PurpleCardBackground
            ),
            border = BorderStroke(1.dp, PurpleCardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (isStep2Enabled) Color(0xFFCE93D8) else PurpleSecondaryText.copy(alpha = 0.5f),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.wizard_step_otg_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.wizard_msg_otg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PurpleAccentText
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (otgUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wizard_access_granted),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81C784)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onNextToStep3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleButtonColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_btn_proceed_to_archive),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onStartOtgRegistration,
                        enabled = isStep2Enabled && isPhysConnected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleButtonColor,
                            contentColor = Color.White,
                            disabledContainerColor = PurpleButtonColor.copy(alpha = 0.35f),
                            disabledContentColor = Color.White.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_btn_register_otg),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isPhysConnected && isStep2Enabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.welcome_msg_drive_not_detected),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF9A9A),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.wizard_btn_skip),
                color = PurpleSecondaryText,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WizardStep3ArchiveSetup(
    otgUri: Uri?,
    onScanArchives: suspend (Uri) -> List<ArchiveEntity>,
    onSelectExistingArchive: (ArchiveEntity, Uri) -> Unit,
    onCreateNewArchive: (String, Uri) -> Unit,
    onScanMediaFolders: ((List<DiscoveredFolder>) -> Unit) -> Unit,
    onAddDiscoveredFolder: (DiscoveredFolder) -> Unit,
    onStartOtgRegistration: () -> Unit = {}
) {
    var isScanning by remember(otgUri) { mutableStateOf(otgUri != null) }
    var foundArchives by remember { mutableStateOf<List<ArchiveEntity>>(emptyList()) }
    var newArchiveName by remember { mutableStateOf("") }
    var showCreateForm by remember { mutableStateOf(false) }
    var isScanningMediaFolders by remember { mutableStateOf(false) }
    var discoveredFolders by remember { mutableStateOf<List<DiscoveredFolder>?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    LaunchedEffect(otgUri) {
        if (otgUri != null) {
            isScanning = true
            try {
                val archives = onScanArchives(otgUri)
                foundArchives = archives
                if (archives.isEmpty()) {
                    showCreateForm = true
                }
            } catch (_: Exception) {
                foundArchives = emptyList()
                showCreateForm = true
            } finally {
                isScanning = false
            }
        } else {
            isScanning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Step 3 Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PurpleIconBox),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = Color(0xFFCE93D8),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.wizard_step_archive_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (otgUri == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PurpleCardBackground),
                border = BorderStroke(1.dp, PurpleCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = Color(0xFFCE93D8),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.welcome_msg_drive_not_detected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PurpleAccentText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onStartOtgRegistration,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleButtonColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_btn_register_otg),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else if (isScanning) {
            // Scanning spinner state
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PurpleCardBackground),
                border = BorderStroke(1.dp, PurpleCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFCE93D8),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.wizard_archive_scanning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PurpleAccentText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (discoveredFolders != null) {
            // View Discovered Photo Folders
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PurpleCardBackground),
                border = BorderStroke(1.dp, PurpleCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_folders_found_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        TextButton(onClick = { discoveredFolders = null }) {
                            Text(
                                text = stringResource(R.string.btn_cancel),
                                color = Color(0xFFCE93D8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isScanningMediaFolders) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFCE93D8),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.wizard_folders_scanning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PurpleAccentText,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (discoveredFolders.isNullOrEmpty()) {
                        Text(
                            text = stringResource(R.string.dialog_discovered_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PurpleSecondaryText,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        discoveredFolders?.forEach { folder ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF331B58)
                                ),
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
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
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
                                            color = PurpleSecondaryText
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
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

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { discoveredFolders = null },
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
        } else {
            // Main Archive Selection & Creation View
            if (foundArchives.isNotEmpty()) {
                // Scenario 1: Found archives
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PurpleCardBackground),
                    border = BorderStroke(1.dp, PurpleCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.wizard_archive_found_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        foundArchives.forEach { archive ->
                            Card(
                                onClick = {
                                    if (otgUri != null && !isOperating) {
                                        isOperating = true
                                        onSelectExistingArchive(archive, otgUri)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF331B58)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.35f))
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
                                            .background(PurpleIconBox),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = Color(0xFFCE93D8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = archive.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = archive.folderName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PurpleSecondaryText
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color(0xFFCE93D8)
                                    )
                                }
                            }
                        }

                        if (!showCreateForm) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showCreateForm = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF3B1E6B),
                                    contentColor = Color(0xFFE1D5F5)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.wizard_archive_create_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Soft Banner: No archives found
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF23143D)),
                    border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFFB39DDB),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.wizard_archive_not_found_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PurpleAccentText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Inline Create Archive Form
            if (showCreateForm || foundArchives.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PurpleCardBackground),
                    border = BorderStroke(1.dp, PurpleCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.wizard_archive_create_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (foundArchives.isNotEmpty()) {
                                TextButton(onClick = { showCreateForm = false }) {
                                    Text(
                                        text = stringResource(R.string.btn_cancel),
                                        color = Color(0xFFCE93D8)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.wizard_archive_create_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = PurpleSecondaryText
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newArchiveName,
                            onValueChange = { if (it.length <= 30) newArchiveName = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.wizard_archive_name_placeholder),
                                    color = PurpleSecondaryText.copy(alpha = 0.6f)
                                )
                            },
                            singleLine = true,
                            maxLines = 1,
                            supportingText = {
                                Text(
                                    text = "${newArchiveName.length}/30",
                                    color = PurpleSecondaryText,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFCE93D8),
                                unfocusedBorderColor = PurpleCardBorder,
                                cursorColor = Color(0xFFCE93D8),
                                focusedContainerColor = Color(0xFF1D0E35),
                                unfocusedContainerColor = Color(0xFF1D0E35)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (otgUri != null && newArchiveName.isNotBlank() && !isOperating) {
                                    isOperating = true
                                    onCreateNewArchive(newArchiveName.trim(), otgUri)
                                }
                            },
                            enabled = newArchiveName.isNotBlank() && otgUri != null && !isOperating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurpleButtonColor,
                                contentColor = Color.White,
                                disabledContainerColor = PurpleButtonColor.copy(alpha = 0.35f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.wizard_btn_create_archive),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Button: Connect existing photo folder on drive
            Button(
                onClick = {
                    isScanningMediaFolders = true
                    discoveredFolders = emptyList()
                    onScanMediaFolders { folders ->
                        discoveredFolders = folders
                        isScanningMediaFolders = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF331B58),
                    contentColor = Color(0xFFE1D5F5)
                ),
                border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.wizard_btn_import_photo_folder),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoGuidePlayer(
    rawResourceId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            volume = 0f // Mute audio
        }
    }

    LaunchedEffect(rawResourceId) {
        if (rawResourceId != 0) {
            val rawUri = Uri.parse("android.resource://${context.packageName}/$rawResourceId")
            val mediaItem = MediaItem.fromUri(rawUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier
    )
}
