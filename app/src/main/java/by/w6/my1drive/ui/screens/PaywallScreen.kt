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
import by.w6.my1drive.billing.IBillingManager
import by.w6.my1drive.billing.PurchaseState


@Composable
fun PaywallScreen(
    billingManager: IBillingManager,
    missingPhotos: Int,
    missingVideos: Int,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    onPromoCode: (() -> Unit)? = null
) {
    val purchaseState by billingManager.purchaseState.collectAsState()
    val product by billingManager.productPriceText.collectAsState()

    LaunchedEffect(Unit) {
        billingManager.resetState()
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
                if (missingPhotos > 0 || missingVideos > 0) {
                    Text(
                        text = stringResource(R.string.paywall_description_partial, missingPhotos, missingVideos),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.paywall_description),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.paywall_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                when (val state = purchaseState) {
                    is PurchaseState.Loading -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.paywall_loading), modifier = Modifier.padding(top = 8.dp))
                    }
                    is PurchaseState.Error -> {
                        Text(
                            text = stringResource(R.string.paywall_error, state.message),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    billingManager.resetState()
                                    billingManager.loadProducts()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.paywall_btn_retry))
                            }
                            Button(
                                onClick = { billingManager.purchasePremium() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.paywall_btn_buy_pro))
                            }
                        }
                    }
                    is PurchaseState.Purchasing -> {
                        CircularProgressIndicator()
                    }
                    else -> {
                        val prod = product
                        if (prod != null) {
                            val priceText = prod
                            Button(
                                onClick = { billingManager.purchasePremium() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(stringResource(R.string.paywall_buy_button, priceText))
                            }
                        } else {
                            Button(
                                onClick = { billingManager.purchasePremium() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(stringResource(R.string.paywall_btn_buy_pro))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                onPromoCode?.let { onPromo ->
                    TextButton(onClick = onPromo) {
                        Text(stringResource(R.string.have_promo_code))
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = purchaseState !is PurchaseState.Purchasing
                ) {
                    Text(stringResource(R.string.paywall_close))
                }
            }
        }
    )
}
