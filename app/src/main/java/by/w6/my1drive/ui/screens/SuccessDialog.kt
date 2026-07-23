package by.w6.my1drive.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun SuccessDialog(
    storageBeforeGb: Float,
    storageAfterGb: Float,
    onDismiss: () -> Unit,
    onViewOnUsbClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Архивация завершена!") },
        text = { 
            Text(text = "Освобождено: было $storageBeforeGb ГБ → стало $storageAfterGb ГБ") 
        },
        confirmButton = {
            TextButton(onClick = onViewOnUsbClick) {
                Text("Посмотреть на флешке")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
