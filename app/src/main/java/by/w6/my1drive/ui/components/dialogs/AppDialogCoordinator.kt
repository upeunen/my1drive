package by.w6.my1drive.ui.components.dialogs

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import by.w6.my1drive.R
import by.w6.my1drive.ui.AppDialog
import by.w6.my1drive.ui.GalleryViewModel
import by.w6.my1drive.ui.ArchiveNamingDialog
import by.w6.my1drive.ui.CreateArchiveGuideDialog
import by.w6.my1drive.ui.components.UnknownDriveDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ChevronRight
import by.w6.my1drive.ui.archiveStripeColor
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import by.w6.my1drive.ui.VideoGuidePlayer
import by.w6.my1drive.ui.screens.SuccessDialog
import by.w6.my1drive.ui.screens.PaywallScreen
// import by.w6.my1drive.ui.UnreadableOtgDialog
import by.w6.my1drive.ui.WriteProtectedRootDialog

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AppDialogCoordinator(
    activeDialog: AppDialog?,
    viewModel: GalleryViewModel,
    currentScreenRoute: String,
    onSelectOtgDirectory: () -> Unit,
    onSelectDeviceDirectory: () -> Unit,
    onNavigateToTab: (String) -> Unit
) {
    val isWaitingOtgMount by viewModel.isWaitingOtgMount.collectAsStateWithLifecycle()
    if (isWaitingOtgMount) {
        Dialog(
            onDismissRequest = { viewModel.setWaitingOtgMount(false) },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.otg_mount_dialog_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.otg_mount_dialog_msg),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.setWaitingOtgMount(false) }) {
                            Text(text = stringResource(id = R.string.btn_cancel))
                        }
                    }
                }
            }
        }
    }

    if (activeDialog == null) return

    when (activeDialog) {
        is AppDialog.SetupWizard -> {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isPhysConnected by viewModel.isPhysConnected.collectAsStateWithLifecycle()
            val hasStoragePermission by viewModel.hasAllFilesAccess.collectAsStateWithLifecycle()
            val otgUri by viewModel.otgManager.otgDirectoryUri.collectAsStateWithLifecycle()
            by.w6.my1drive.ui.SetupWizardScreen(
                initialStep = activeDialog.initialStep,
                uiState = uiState,
                isPhysConnected = isPhysConnected,
                hasStoragePermission = hasStoragePermission,
                otgUri = otgUri,
                onDismiss = { viewModel.completeSetupWizard() },
                onSkip = { viewModel.completeSetupWizard() },
                onStartOtgRegistration = {
                    onSelectOtgDirectory()
                },
                onRequestFullAccess = {
                    viewModel.proceedWithManageStorageRequest(null, keepActiveDialog = true)
                },
                onScanArchives = { uri -> viewModel.findArchivesOnDrive(uri) },
                onSelectExistingArchive = { archive, uri ->
                    viewModel.selectArchive(archive, uri)
                    viewModel.completeSetupWizard()
                },
                onSelectExistingArchives = { archives, uri ->
                    viewModel.selectArchives(archives, uri)
                    viewModel.completeSetupWizard()
                },
                onCreateNewArchive = { name, uri ->
                    viewModel.otgManager.saveOtgArchive(uri, name)
                    viewModel.completeSetupWizard()
                },
                onScanMediaFolders = { onFound ->
                    viewModel.scanForMediaFolders(maxDepth = 2, onResult = onFound)
                },
                onAddDiscoveredFolder = { folder ->
                    viewModel.addDiscoveredFolderAsArchive(folder) {
                        viewModel.completeSetupWizard()
                    }
                },
                onFinish = { viewModel.completeSetupWizard() }
            )
        }
        is AppDialog.FirstLaunch -> {}
        is AppDialog.Paywall -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            PaywallScreen(
                billingManager = viewModel.billingManager,
                missingPhotos = activeDialog.missingPhotos,
                missingVideos = activeDialog.missingVideos,
                onSuccess = {
                    viewModel.dismissDialog()
                    android.widget.Toast.makeText(
                        context,
                        context.getString(by.w6.my1drive.R.string.toast_premium_activated),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                },
                onDismiss = { viewModel.dismissDialog() },
                onPromoCode = { viewModel.showPromoCodeDialog() }
            )
        }
        is AppDialog.UnknownDrive -> {
            UnknownDriveDialog(
                onCreateNew = {
                    viewModel.dismissDialog()
                    onSelectOtgDirectory()
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is AppDialog.UnreadableOtg -> {}
        is AppDialog.WriteProtectedRoot -> {
            WriteProtectedRootDialog(
                onRetry = {
                    viewModel.dismissDialog()
                    onSelectOtgDirectory()
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is AppDialog.LocalFolder -> {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isPhysConnected by viewModel.isPhysConnected.collectAsStateWithLifecycle()
            val hasStoragePermission by viewModel.hasAllFilesAccess.collectAsStateWithLifecycle()
            by.w6.my1drive.ui.SetupWizardScreen(
                initialStep = 1,
                uiState = uiState,
                isPhysConnected = isPhysConnected,
                hasStoragePermission = hasStoragePermission,
                onDismiss = { viewModel.dismissDialog() },
                onStartOtgRegistration = {
                    viewModel.dismissDialog()
                    onSelectOtgDirectory()
                },
                onRequestFullAccess = {
                    viewModel.proceedWithManageStorageRequest(null, keepActiveDialog = true)
                },
                onFinish = { viewModel.dismissDialog() }
            )
        }
        is AppDialog.Naming -> {
            ArchiveNamingDialog(
                onConfirm = { name ->
                    viewModel.dismissDialog()
                    viewModel.otgManager.saveOtgArchive(activeDialog.uri, name)
                    viewModel.refresh()
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is AppDialog.CreateArchiveGuide -> {
            CreateArchiveGuideDialog(
                onConfirm = {
                    viewModel.dismissDialog()
                    viewModel.showNamingDialog(activeDialog.uri)
                },
                onScanMediaFolders = {
                    viewModel.dismissDialog()
                    viewModel.openDiscoveredArchivesSheet()
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is AppDialog.Success -> {
            SuccessDialog(
                freedSpaceBytes = activeDialog.data.freedSpaceBytes,
                currentFreeSpaceBytes = activeDialog.data.currentFreeSpaceBytes,
                totalSpaceBytes = activeDialog.data.totalSpaceBytes,
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is AppDialog.UsbTooltip -> {
            if (currentScreenRoute == "archive") {
                AlertDialog(
                    onDismissRequest = { viewModel.markUsbTooltipSeen() },
                    title = { Text(stringResource(id = R.string.tooltip_title_hint)) },
                    text = { Text(stringResource(id = R.string.tooltip_media_moved)) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.markUsbTooltipSeen() }) {
                            Text(stringResource(id = R.string.dialog_got_it))
                        }
                    }
                )
            }
        }

        is AppDialog.ManageStoragePermission -> {
            val context = LocalContext.current
            val rawResourceId = remember(context) {
                val id = context.resources.getIdentifier("manage_media_guide", "raw", context.packageName)
                if (id == 0) {
                    context.resources.getIdentifier("instr", "raw", context.packageName)
                } else id
            }

            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null
                    )
                },
                title = { Text(stringResource(id = R.string.dialog_manage_storage_title)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (rawResourceId != 0) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                VideoGuidePlayer(rawResourceId = rawResourceId)
                            }
                        }
                        Text(stringResource(id = R.string.dialog_manage_storage_desc))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.proceedWithManageStorageRequest(activeDialog.itemsToWait) }) {
                        Text(stringResource(id = R.string.dialog_manage_storage_btn))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(id = R.string.action_cancel))
                    }
                }
            )
        }

        is AppDialog.CreateFolder -> {}
        is AppDialog.ArchiveFolderAccess -> {}

        is AppDialog.PromoCode -> {
            PromoCodeDialog(
                onApply = { code -> viewModel.applyPromoCode(code) },
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is AppDialog.PromoSuccess -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text(stringResource(R.string.promo_success_title)) },
                text = {
                    Text(
                        activeDialog.customMessage ?: stringResource(R.string.promo_success_message, activeDialog.days)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        }

        is AppDialog.Announcement -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text(activeDialog.title) },
                text = { Text(activeDialog.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        }

        is AppDialog.SelectArchive -> {
            val activeArchiveUuid by viewModel.otgManager.activeArchiveUuid.collectAsStateWithLifecycle()
            val isWaitingMount by viewModel.isWaitingOtgMount.collectAsStateWithLifecycle()
            ArchivesAndFoldersDialog(
                archives = activeDialog.archives,
                activeArchiveUuid = activeArchiveUuid,
                uri = activeDialog.uri,
                isWaitingMount = isWaitingMount,
                onSelectArchive = { archive ->
                    viewModel.selectArchive(archive, activeDialog.uri)
                },
                onSelectArchives = { archives ->
                    viewModel.selectArchives(archives, activeDialog.uri)
                },
                onCreateNewArchive = { name, uri ->
                    viewModel.otgManager.saveOtgArchive(uri, name)
                    viewModel.dismissDialog()
                },
                onScanMediaFolders = { depth, onResult ->
                    viewModel.scanForMediaFolders(maxDepth = depth, onResult = onResult)
                },
                onAddDiscoveredFolder = { folder ->
                    viewModel.addDiscoveredFolderAsArchive(folder) {
                        viewModel.dismissDialog()
                    }
                },
                onSelectOtgDirectory = {
                    viewModel.dismissDialog()
                    onSelectOtgDirectory()
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }

        is AppDialog.SelectTargetArchive -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = {
                    Text(
                        text = stringResource(by.w6.my1drive.R.string.select_target_archive_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(by.w6.my1drive.R.string.select_target_archive_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(activeDialog.archives) { archive ->
                                val archiveColor = archiveStripeColor(archive.uuid, activeDialog.archives)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectTargetArchiveAndProceed(
                                                archive,
                                                activeDialog.targetUri,
                                                activeDialog.isCopy
                                            )
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .background(archiveColor, CircleShape)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = archive.name.ifBlank { archive.folderName.ifBlank { archive.uuid.take(8) } },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (archive.folderName.isNotBlank()) {
                                                Text(
                                                    text = archive.folderName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(by.w6.my1drive.R.string.action_cancel))
                    }
                }
            )
        }

        is AppDialog.NewDriveMiniWizard -> {
            NewDriveMiniWizardDialog(
                initialStep = activeDialog.step,
                errorMessage = activeDialog.errorMessage,
                driveUri = activeDialog.driveUri,
                onRequestSelectOtgFolder = {
                    onSelectOtgDirectory()
                },
                onScanArchives = { uri ->
                    viewModel.findArchivesOnDrive(uri)
                },
                onSelectArchives = { archives, uri ->
                    viewModel.selectArchives(archives, uri)
                },
                onCreateNewArchive = { name, uri ->
                    viewModel.otgManager.saveOtgArchive(uri, name)
                },
                onDismiss = {
                    viewModel.dismissDialog()
                }
            )
        }
    }
}
