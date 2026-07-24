package by.w6.my1drive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.billing.RuStoreBillingManager
import by.w6.my1drive.billing.PurchaseState

@Composable
fun PaywallScreen(
    billingManager: RuStoreBillingManager,
    missingPhotos: Int,
    missingVideos: Int,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val purchaseState by billingManager.purchaseState.collectAsState()
    val product by billingManager.premiumProduct.collectAsState()

    LaunchedEffect(Unit) {
        billingManager.loadProducts()
    }

    LaunchedEffect(purchaseState) {
        if (purchaseState is PurchaseState.Success) {
            onSuccess()
        }
    }

    AlertDialog(
        onDismissRequest = { 
            if (purchaseState !is PurchaseState.Purchasing) {
                onDismiss() 
            }
        },
        title = { Text(stringResource(R.string.paywall_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.paywall_description),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (missingPhotos > 0 || missingVideos > 0) {
                    val limitText = buildString {
                        append("Не удалось заархивировать: ")
                        if (missingPhotos > 0) append("$missingPhotos фото ")
                        if (missingVideos > 0) append("$missingVideos видео")
                    }
                    Text(
                        text = limitText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                when (val state = purchaseState) {
                    is PurchaseState.Loading -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.paywall_loading), modifier = Modifier.padding(top = 8.dp))
                    }
                    is PurchaseState.Error -> {
                        Text(
                            text = stringResource(R.string.paywall_error, state.message),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    is PurchaseState.Purchasing -> {
                        CircularProgressIndicator()
                    }
                    else -> {
                        product?.let { prod ->
                            val priceText = prod.amountLabel?.value ?: "..."
                            Button(
                                onClick = { billingManager.purchasePremium() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(stringResource(R.string.paywall_buy_button, priceText))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = purchaseState !is PurchaseState.Purchasing
            ) {
                Text(stringResource(R.string.paywall_close))
            }
        }
    )
}
