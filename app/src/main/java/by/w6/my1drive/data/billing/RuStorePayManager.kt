package by.w6.my1drive.data.billing

import ru.rustore.sdk.pay.*
import ru.rustore.sdk.pay.model.*
import by.w6.my1drive.analytics.AppAnalytics

class RuStorePayManager {

    fun checkUserPurchases(onSuccess: (Boolean) -> Unit, onFailure: (Throwable) -> Unit) {
        RuStorePayClient.instance.getPurchaseInteractor().getPurchases(
            productType = ProductType.NON_CONSUMABLE_PRODUCT,
            purchaseStatus = ProductPurchaseStatus.CONFIRMED
        ).addOnSuccessListener { purchases ->
            val hasPremium = purchases.filterIsInstance<ProductPurchase>().any { it.productId.value == "my1drive_premium_unlock" }
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
                AppAnalytics.logPurchaseSuccess()
                onSuccess(result)
            }
            .addOnFailureListener { error ->
                when (error) {
                    is RuStorePaymentException.ProductPurchaseCancelled -> {
                        AppAnalytics.logPurchaseCancelled()
                        onCancelled()
                    }
                    is RuStorePaymentException.ProductPurchaseException -> {
                        AppAnalytics.logPurchaseError(error.message ?: "Unknown error")
                        onError(error)
                    }
                    else -> {
                        AppAnalytics.logPurchaseError(error.message ?: "Unknown exception: ${error.javaClass.simpleName}")
                        onError(error)
                    }
                }
            }
    }
}
