package by.w6.my1drive.ui.components

import androidx.compose.ui.res.stringResource
import by.w6.my1drive.R
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import by.w6.my1drive.ui.RestoreConflict
import by.w6.my1drive.ui.RestoreConflictDecision

@Composable
fun RestoreConflictDialog(
    conflict: RestoreConflict,
    onDecision: (RestoreConflictDecision) -> Unit
) {
    var applyToAll by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onDecision(RestoreConflictDecision(uri = uri, applyToAll = applyToAll))
        }
    }

    val fallbackFolderName = conflict.defaultFallbackPath.removeSuffix("/")

    AlertDialog(
        onDismissRequest = { 
            // Interpret dismiss as skipping the file.
            onDecision(RestoreConflictDecision(uri = null, applyToAll = false))
        },
        title = { Text(stringResource(R.string.restore_conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.restore_conflict_desc, conflict.itemDisplayName))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.restore_conflict_instruction))
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it }
                    )
                    Text(stringResource(R.string.restore_conflict_apply_to_all))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Use null to indicate default MediaStore fallback
                    onDecision(RestoreConflictDecision(uri = null, applyToAll = applyToAll))
                }
            ) {
                Text(stringResource(R.string.restore_conflict_btn_restore_in, fallbackFolderName))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    folderPickerLauncher.launch(null)
                }
            ) {
                Text(stringResource(R.string.restore_conflict_btn_choose_where))
            }
        }
    )
}