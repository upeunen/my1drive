package by.w6.my1drive.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Usb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.R

@Composable
fun MissingFilesDialog(missingNames: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_sync_missing_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.auto_sync_missing_msg, missingNames.joinToString("\n"))) },
        confirmButton = { Button(onClick = onDismiss) { Text(text = stringResource(R.string.btn_ok), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false) } }
    )
}

@Composable
fun UnknownDriveDialog(
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(by.w6.my1drive.R.string.unknown_media_drive_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text(stringResource(by.w6.my1drive.R.string.unknown_media_drive_desc))
        },
        confirmButton = {
            Button(onClick = onCreateNew) {
                Text(
                    text = stringResource(by.w6.my1drive.R.string.btn_create_new),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(by.w6.my1drive.R.string.btn_close),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    pendingDelete: List<MediaItem>,
    isArchiveTab: Boolean,
    isOtgConnected: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (pendingDelete.size == 1) stringResource(R.string.delete_confirm_title) else "${stringResource(R.string.delete_confirm_title)} (${pendingDelete.size})",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (pendingDelete.size == 1) {
                    Text(stringResource(R.string.delete_confirm_msg, pendingDelete.first().displayName))
                } else {
                    Text(stringResource(R.string.action_delete_files_question, pendingDelete.size))
                }
                if (isArchiveTab) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.delete_archived_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(
                    text = if (pendingDelete.size == 1) {
                        if (isArchiveTab) stringResource(R.string.btn_delete_otg) else stringResource(R.string.btn_delete_file)
                    } else {
                        stringResource(R.string.action_delete_all)
                    },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    )
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (startDateMillis: Long?, endDateMillis: Long?) -> Unit
) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateRangeSelected(state.selectedStartDateMillis, state.selectedEndDateMillis)
                }
            ) {
                Text(
                    text = stringResource(by.w6.my1drive.R.string.btn_choose),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(by.w6.my1drive.R.string.btn_cancel),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    text = stringResource(by.w6.my1drive.R.string.btn_select_date_range),
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp)
                )
            },
            headline = {
                // Default headline behaves fine, but we can customize if needed
            },
            showModeToggle = false,
            modifier = Modifier.weight(1f)
        )
    }
}
