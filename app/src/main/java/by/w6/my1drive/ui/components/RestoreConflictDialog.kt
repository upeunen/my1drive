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

    AlertDialog(
        onDismissRequest = { 
            // Interpret dismiss as skipping the file.
            onDecision(RestoreConflictDecision(uri = null, applyToAll = false))
        },
        title = { Text("Ошибка восстановления пути") },
        text = {
            Column {
                Text("Файл \"${conflict.itemDisplayName}\" имеет несовместимый путь (возможно, сохранен с другого устройства).")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Он будет восстановлен в стандартную папку: ${conflict.defaultFallbackPath}")
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it }
                    )
                    Text("Применить ко всем последующим конфликтам")
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
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    folderPickerLauncher.launch(null)
                }
            ) {
                Text("Выбрать папку")
            }
        }
    )
}
