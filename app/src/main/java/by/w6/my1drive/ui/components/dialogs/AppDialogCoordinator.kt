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
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun AppDialogCoordinator(
    activeDialog: AppDialog?,
    viewModel: GalleryViewModel,
    currentScreenRoute: String,
    onSelectOtgDirectory: () -> Unit,
    onSelectDeviceDirectory: () -> Unit,
    onNavigateToTab: (String) -> Unit
) {
    if (activeDialog == null) return

    when (activeDialog) {
        is AppDialog.SetupWizard -> {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            by.w6.my1drive.ui.SetupWizardDialog(
                initialStep = activeDialog.initialStep,
                uiState = uiState,
                onDismiss = { viewModel.dismissDialog() },
                onStartOtgRegistration = {
                    onSelectOtgDirectory()
                },
                onRequestFullAccess = {
                    viewModel.dismissDialog()
                    viewModel.proceedWithManageStorageRequest(null)
                },
                onFinish = { viewModel.dismissDialog() }
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
            by.w6.my1drive.ui.SetupWizardDialog(
                initialStep = 2,
                uiState = uiState,
                onDismiss = { viewModel.dismissDialog() },
                onStartOtgRegistration = {
                    onSelectOtgDirectory()
                },
                onRequestFullAccess = {
                    viewModel.dismissDialog()
                    viewModel.proceedWithManageStorageRequest(null)
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
    }
}
