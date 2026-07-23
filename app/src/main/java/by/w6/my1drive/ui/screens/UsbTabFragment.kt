package by.w6.my1drive.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun UsbTabFragment(
    isFirstStart: Boolean,
    onTooltipDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Box(modifier = modifier) {
        content() // Содержимое вкладки "Флешка"

        if (isFirstStart) {
            AlertDialog(
                onDismissRequest = onTooltipDismissed,
                title = { Text("Обучение") },
                text = { Text("Ваши тяжелые оригиналы теперь здесь, а в галерее телефона остались легкие превью") },
                confirmButton = {
                    TextButton(onClick = onTooltipDismissed) {
                        Text("Понятно")
                    }
                }
            )
        }
    }
}
