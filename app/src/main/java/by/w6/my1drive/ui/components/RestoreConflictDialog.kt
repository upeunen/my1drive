package by.w6.my1drive.ui.components

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
        title = { Text("Путь восстановления недоступен") },
        text = {
            Column {
                Text("Не удалось получить доступ к целевой папке для восстановления файла «${conflict.itemDisplayName}».")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Пожалуйста, укажите, куда восстановить файл:")
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it }
                    )
                    Text("Применить ко всем последующим")
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
                Text("Восстановить в $fallbackFolderName")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    folderPickerLauncher.launch(null)
                }
            ) {
                Text("Выбрать куда")
            }
        }
    )
}
