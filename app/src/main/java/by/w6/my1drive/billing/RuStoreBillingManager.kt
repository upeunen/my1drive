package by.w6.my1drive.billing

import android.app.Application
import android.util.Log
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.appmetrica.analytics.AppMetrica

class RuStoreBillingManager(private val application: Application) {

    private val _premiumProduct = MutableStateFlow<Product?>(null)
    val premiumProduct: StateFlow<Product?> = _premiumProduct.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    /**
     * Загружает информацию о продукте из RuStore.
     */
    fun loadProducts() {
        _purchaseState.value = PurchaseState.Loading
        RuStorePayClient.instance.getProductInteractor()
            .getProducts(listOf(ProductId(PRODUCT_ID)))
            .addOnSuccessListener { products ->
                val product = products.find { it.productId.value == PRODUCT_ID }
                _premiumProduct.value = product
                _purchaseState.value = PurchaseState.Idle
                Log.d("RuStoreBilling", "Product loaded: ${product?.title?.value}")
            }
            .addOnFailureListener { error ->
                Log.e("RuStoreBilling", "Failed to load products", error)
                _purchaseState.value = PurchaseState.Error(error.message ?: "Failed to load products")
            }
    }

    /**
     * Запускает процесс покупки.
     */
    fun purchasePremium() {
        _purchaseState.value = PurchaseState.Purchasing
        
        val params = ProductPurchaseParams(
            productId = ProductId(PRODUCT_ID),
            orderId = null,
            quantity = Quantity(1),
            developerPayload = null,
            appUserId = null,
            appUserEmail = null
        )

        RuStorePayClient.instance.getPurchaseInteractor()
            .purchase(params)
            .addOnSuccessListener { result ->
                AppMetrica.reportEvent("purchase_success")
                _purchaseState.value = PurchaseState.Success
            }
            .addOnFailureListener { error ->
                if (error is ru.rustore.sdk.pay.model.RuStorePaymentException.ProductPurchaseCancelled) {
                    AppMetrica.reportEvent("purchase_cancelled")
                    _purchaseState.value = PurchaseState.Idle
                    Log.d("RuStoreBilling", "Purchase cancelled")
                } else {
                    AppMetrica.reportEvent("purchase_error")
                    Log.e("RuStoreBilling", "Purchase exception", error)
                    _purchaseState.value = PurchaseState.Error(error.message ?: "Purchase exception")
                }
            }
    }

    /**
     * Проверяет купленные ранее товары для восстановления покупок (Restore Purchases).
     * Должен вызываться при запуске приложения.
     */
    fun checkPurchases(onPremiumUnlocked: ((Boolean) -> Unit)) {
        RuStorePayClient.instance.getPurchaseInteractor().getPurchases()
            .addOnSuccessListener { purchases ->
                val hasPremium = purchases.any { purchase ->
                    if (purchase is ru.rustore.sdk.pay.model.ProductPurchase) {
                        purchase.productId.value == PRODUCT_ID && 
                        (purchase.status == ProductPurchaseStatus.CONFIRMED || 
                         purchase.status == ProductPurchaseStatus.PAID)
                    } else false
                }
                onPremiumUnlocked(hasPremium)
            }
            .addOnFailureListener { error ->
                Log.e("RuStoreBilling", "Failed to check purchases", error)
            }
    }
    
    fun resetState() {
        _purchaseState.value = PurchaseState.Idle
    }

    companion object {
        const val PRODUCT_ID = "premium_unlock"
    }
}

sealed class PurchaseState {
    object Idle : PurchaseState()
    object Loading : PurchaseState()
    object Purchasing : PurchaseState()
    object Success : PurchaseState()
    data class Error(val message: String) : PurchaseState()
}
