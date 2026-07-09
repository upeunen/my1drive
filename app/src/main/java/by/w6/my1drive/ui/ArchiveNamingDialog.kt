package by.w6.my1drive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ArchiveNamingDialog(
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val isError = text.trim().isEmpty() || text.length > 30

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Назовите этот архив",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "Введите понятное название для этого накопителя (например: Флешка USB-C, Архив 2026), чтобы не перепутать его с другими дисками.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 35) text = it },
                    label = { Text("Название архива") },
                    singleLine = true,
                    isError = isError,
                    supportingText = {
                        if (text.length > 30) {
                            Text("Максимум 30 символов")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (!isError) onConfirm(text.trim()) },
                enabled = !isError
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
