package by.w6.my1drive.ui.components.dialogs

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import by.w6.my1drive.R
import by.w6.my1drive.ui.AppDialog
import by.w6.my1drive.ui.GalleryViewModel
import by.w6.my1drive.ui.ArchiveNamingDialog
import by.w6.my1drive.ui.CreateArchiveGuideDialog
import by.w6.my1drive.ui.FirstLaunchDialog
import by.w6.my1drive.ui.LocalFolderDialog
import by.w6.my1drive.ui.screens.SuccessDialog
import by.w6.my1drive.ui.UnknownDriveDialog
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
        is AppDialog.FirstLaunch -> {
            val isOtgConnected by viewModel.isOtgConnected.collectAsState()
            FirstLaunchDialog(
                isOtgConnected = isOtgConnected,
                onStart = {
                    viewModel.dismissDialog()
                    onSelectOtgDirectory()
                },
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is AppDialog.Paywall -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text("Купить Безлимит") },
                text = { Text("Здесь будет интерфейс Пейвола (UI еще в разработке).") },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text("Закрыть")
                    }
                }
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
            val pendingFolder by viewModel.pendingDeviceFolderToRequest.collectAsState()
            LocalFolderDialog(
                folderPath = pendingFolder ?: "DCIM",
                onSelectFolder = {
                    viewModel.dismissDialog()
                    onSelectDeviceDirectory()
                },
                onDismiss = { viewModel.dismissDialog() }
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
                storageBeforeGb = activeDialog.data.storageBeforeGb,
                storageAfterGb = activeDialog.data.storageAfterGb,
                onDismiss = { viewModel.dismissDialog() },
                onViewOnUsbClick = {
                    viewModel.dismissDialog()
                    onNavigateToTab("archive")
                }
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
        is AppDialog.LimitReached -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            AlertDialog(
                onDismissRequest = { viewModel.dismissLimitReachedDialog() },
                title = { Text("Лимит бесплатной версии", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                text = { Text("Вы достигли лимита бесплатной версии в 128 МБ. Для продолжения архивации необходимо приобрести PRO версию либо удалить часть фото из архива.") },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = { viewModel.dismissLimitReachedDialog() }) {
                        Text(
                            text = "ОК",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        android.widget.Toast.makeText(context, "Покупка PRO версии временно недоступна", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Text(
                            text = "Купить PRO",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
            )
        }
        is AppDialog.CreateFolder -> {}
        is AppDialog.ArchiveFolderAccess -> {}
    }
}
