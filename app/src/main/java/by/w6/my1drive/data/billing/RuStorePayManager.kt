package by.w6.my1drive.data.billing

// TODO: Add necessary RuStore Pay SDK imports here

class RuStorePayManager {

    fun checkUserPurchases(onSuccess: (Boolean) -> Unit, onFailure: (Throwable) -> Unit) {
        RuStorePayClient.instance.getPurchaseInteractor().getPurchases(
            productType = ProductType.NON_CONSUMABLE_PRODUCT,
            purchaseStatus = ProductPurchaseStatus.CONFIRMED
        ).addOnSuccessListener { purchases ->
            val hasPremium = purchases.any { it.productId.value == "my1drive_premium_unlock" }
            onSuccess(hasPremium)
        }.addOnFailureListener { error ->
            onFailure(error)
        }
    }

    fun buyPremium(
        onSuccess: (ProductPurchaseResult) -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val params = ProductPurchaseParams(
            productId = ProductId("my1drive_premium_unlock")
        )

        RuStorePayClient.instance.getPurchaseInteractor()
            .purchase(
                params = params,
                preferredPurchaseType = PreferredPurchaseType.ONE_STEP,
                sdkTheme = SdkTheme.LIGHT
            )
            .addOnSuccessListener { result ->
                onSuccess(result)
            }
            .addOnFailureListener { throwable ->
                when (throwable) {
                    is RuStorePaymentException.ProductPurchaseCancelled -> onCancelled()
                    else -> onError(throwable)
                }
            }
    }
}
