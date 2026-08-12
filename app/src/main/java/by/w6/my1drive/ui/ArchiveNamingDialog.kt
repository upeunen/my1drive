package by.w6.my1drive.ui

import androidx.compose.ui.res.stringResource
import by.w6.my1drive.R
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
                text = stringResource(R.string.naming_dialog_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.naming_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 35) text = it },
                    label = { Text(stringResource(R.string.naming_dialog_label)) },
                    singleLine = true,
                    isError = isError,
                    supportingText = {
                        if (text.length > 30) {
                            Text(stringResource(R.string.naming_dialog_max_chars))
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
                Text(stringResource(R.string.naming_dialog_btn_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.naming_dialog_btn_cancel))
            }
        }
    )
}