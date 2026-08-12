package by.w6.my1drive.ui.screens

import androidx.compose.ui.res.stringResource
import by.w6.my1drive.R
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
                title = { Text(stringResource(R.string.usb_tab_tutorial)) },
                text = { Text(stringResource(R.string.usb_tab_tutorial_desc)) },
                confirmButton = {
                    TextButton(onClick = onTooltipDismissed) {
                        Text(stringResource(R.string.usb_tab_got_it))
                    }
                }
            )
        }
    }
}