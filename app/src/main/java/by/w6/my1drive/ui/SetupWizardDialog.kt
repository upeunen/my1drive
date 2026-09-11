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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    onDismiss: () -> Unit,
    onStartOtgRegistration: () -> Unit,
    onRequestFullAccess: () -> Unit,
    onFinish: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val startPage = if (initialStep >= 1) 1 else 0
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { 2 })

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(0)
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
            // Step Indicators at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { iteration ->
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
                        onRequestFullAccess = onRequestFullAccess,
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
    onDismiss: () -> Unit,
    onStartOtgRegistration: () -> Unit,
    onRequestFullAccess: () -> Unit,
    onFinish: () -> Unit
) {
    SetupWizardScreen(
        initialStep = initialStep,
        uiState = uiState,
        isPhysConnected = isPhysConnected,
        hasStoragePermission = hasStoragePermission,
        onDismiss = onDismiss,
        onStartOtgRegistration = onStartOtgRegistration,
        onRequestFullAccess = onRequestFullAccess,
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
    onRequestFullAccess: () -> Unit,
    onStartOtgRegistration: () -> Unit
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
        // Video guide at top
        if (currentVideoRes != 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF190B2E)
                ),
                border = BorderStroke(1.dp, Color(0xFF673AB7).copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                VideoGuidePlayer(rawResourceId = currentVideoRes)
            }
            Spacer(modifier = Modifier.height(16.dp))
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
        modifier = modifier.fillMaxWidth()
    )
}
