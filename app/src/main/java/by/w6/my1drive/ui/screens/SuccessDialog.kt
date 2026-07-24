package by.w6.my1drive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R

@Composable
fun SuccessDialog(
    freedSpaceBytes: Long,
    currentFreeSpaceBytes: Long,
    totalSpaceBytes: Long,
    onDismiss: () -> Unit
) {
    val freedGb = freedSpaceBytes / (1024f * 1024f * 1024f)
    val currentGb = currentFreeSpaceBytes / (1024f * 1024f * 1024f)
    val totalGb = totalSpaceBytes / (1024f * 1024f * 1024f)
    val usedGb = totalGb - currentGb

    val progress = if (totalGb > 0f) usedGb / totalGb else 0f

    val freedStr = String.format(java.util.Locale.US, "%.2f", freedGb)
    val currentStr = String.format(java.util.Locale.US, "%.2f", currentGb)
    val usedStr = String.format(java.util.Locale.US, "%.2f", usedGb)
    val totalStr = String.format(java.util.Locale.US, "%.2f", totalGb)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.success_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = stringResource(id = R.string.success_dialog_freed_space, freedStr))
                Text(text = stringResource(id = R.string.success_dialog_free_space_now, currentStr))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = stringResource(id = R.string.success_dialog_storage_usage, usedStr, totalStr),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = stringResource(id = R.string.success_dialog_view_in_archive_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_close))
            }
        }
    )
}
