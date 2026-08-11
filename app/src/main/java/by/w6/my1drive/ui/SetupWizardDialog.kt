package by.w6.my1drive.ui

import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import by.w6.my1drive.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetupWizardDialog(
    initialStep: Int = 0,
    uiState: GalleryUiState,
    onDismiss: () -> Unit,
    onStartOtgRegistration: () -> Unit,
    onRequestFullAccess: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Check if we need the local folder permission step (Android 11+)
    val needsStoragePermission = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
    }
    
    val pageCount = if (needsStoragePermission) 3 else 2
    val pagerState = rememberPagerState(initialPage = initialStep.coerceIn(0, pageCount - 1), pageCount = { pageCount })

    Dialog(
        onDismissRequest = { /* No dismiss by back button or clicking outside during wizard */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false, // Disable swipe to force using buttons
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> WizardStep1Welcome(uiState)
                        1 -> WizardStep2Otg()
                        2 -> WizardStep3Storage()
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                // Pager indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pageCount) { iteration ->
                        val color = if (pagerState.currentPage == iteration) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(8.dp)
                        )
                    }
                }

                // Buttons for current step
                when (pagerState.currentPage) {
                    0 -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.welcome_btn_start))
                        }
                    }
                    1 -> {
                        Column {
                            Button(
                                onClick = onStartOtgRegistration,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.wizard_btn_register_otg))
                            }
                            TextButton(
                                onClick = {
                                    if (needsStoragePermission) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(2)
                                        }
                                    } else {
                                        onFinish()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.local_folder_dialog_dismiss)) // "Пропустить"
                            }
                        }
                    }
                    2 -> {
                        Button(
                            onClick = onRequestFullAccess,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.local_folder_dialog_full_access),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardStep1Welcome(uiState: GalleryUiState) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(
                imageVector = Icons.Default.Usb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.welcome_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
        
        Text(
            text = stringResource(R.string.welcome_msg),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Show Trial/Limits info
        if (!uiState.isPremiumUnlocked) {
            val trialText = if (uiState.isTrialActive) {
                stringResource(R.string.trial_active_days, uiState.remainingTrialDays)
            } else {
                stringResource(R.string.free_version_limits, uiState.photosArchivedCount, uiState.maxPhotos, uiState.videosArchivedCount, uiState.maxVideos)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(
                    text = trialText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun WizardStep2Otg() {
    val context = LocalContext.current
    val rawResourceId = remember(context) {
        context.resources.getIdentifier("otg_guide", "raw", context.packageName)
    }
    
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(
                imageVector = Icons.Default.Usb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.wizard_title_otg),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
        
        Text(
            text = stringResource(R.string.wizard_msg_otg),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (rawResourceId != 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                VideoGuidePlayer(rawResourceId = rawResourceId)
            }
        }
    }
}

@Composable
private fun WizardStep3Storage() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(
                imageVector = Icons.Default.FolderSpecial,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.local_folder_dialog_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
        
        Text(
            text = stringResource(R.string.local_folder_dialog_desc),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoGuidePlayer(
    rawResourceId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Create ExoPlayer and loop it
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            volume = 0f // Mute audio
        }
    }

    DisposableEffect(rawResourceId) {
        val rawUri = Uri.parse("android.resource://${context.packageName}/$rawResourceId")
        val mediaItem = MediaItem.fromUri(rawUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // Hide progress bar, play/pause buttons
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
