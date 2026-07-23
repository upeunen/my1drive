package by.w6.my1drive.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import by.w6.my1drive.R

@Composable
fun SuccessDialog(
    storageBeforeGb: Float,
    storageAfterGb: Float,
    onDismiss: () -> Unit,
    onViewOnUsbClick: () -> Unit
) {
    val beforeStr = String.format(java.util.Locale.US, "%.2f", storageBeforeGb)
    val afterStr = String.format(java.util.Locale.US, "%.2f", storageAfterGb)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.success_dialog_title)) },
        text = { 
            Text(text = stringResource(id = R.string.success_dialog_freed_space, beforeStr, afterStr)) 
        },
        confirmButton = {
            TextButton(onClick = onViewOnUsbClick) {
                Text(stringResource(id = R.string.success_dialog_view_on_media))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_close))
            }
        }
    )
}
