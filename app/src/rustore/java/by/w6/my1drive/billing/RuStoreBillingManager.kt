package by.w6.my1drive.billing

import android.app.Application
import android.util.Log
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import by.w6.my1drive.utils.DebugLogBuffer

class RuStoreBillingManager(private val application: Application) : IBillingManager {

    private val _productPriceText = MutableStateFlow<String?>(null)
    override val productPriceText: StateFlow<String?> = _productPriceText.asStateFlow()

    private val _premiumProduct = MutableStateFlow<Product?>(null)
    

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    override val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    /**
     * Загружает информацию о продукте из RuStore.
     * Запрашивает как "premium_unlock", так и "my1drive_premium_unlock".
     */
    override fun loadProducts() {
        _purchaseState.value = PurchaseState.Loading
        DebugLogBuffer.log(TAG, "Loading products: $PRODUCT_ID, $ALT_PRODUCT_ID")
        try {
            RuStorePayClient.instance.getProductInteractor()
                .getProducts(listOf(ProductId(PRODUCT_ID), ProductId(ALT_PRODUCT_ID)))
                .addOnSuccessListener { products ->
                    val product = products.find { it.productId.value == PRODUCT_ID || it.productId.value == ALT_PRODUCT_ID }
                    _premiumProduct.value = product
                    _productPriceText.value = product?.amountLabel?.value
                    _purchaseState.value = PurchaseState.Idle
                    val msg = "Product loaded: ${product?.title?.value} (id=${product?.productId?.value})"
                    Log.d(TAG, msg)
                    DebugLogBuffer.log(TAG, msg)
                }
                .addOnFailureListener { error ->
                    val errMsg = error.message ?: "Failed to load products"
                    Log.e(TAG, "Failed to load products", error)
                    DebugLogBuffer.log(TAG, "ERROR loadProducts: $errMsg (${error.javaClass.simpleName})")
                    _purchaseState.value = PurchaseState.Error(errMsg)
                }
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "RuStorePayClient error"
            Log.e(TAG, "Exception initializing loadProducts", e)
            DebugLogBuffer.log(TAG, "EXCEPTION loadProducts: $errMsg (${e.javaClass.simpleName})")
            _purchaseState.value = PurchaseState.Error(errMsg)
        }
    }

    /**
     * Запускает процесс покупки.
     */
    override fun purchasePremium(targetProductId: String?) {
        _purchaseState.value = PurchaseState.Purchasing
        val productIdToBuy = targetProductId ?: _premiumProduct.value?.productId?.value ?: PRODUCT_ID
        DebugLogBuffer.log(TAG, "Initiating purchase for productId: $productIdToBuy")
        
        val params = ProductPurchaseParams(
            productId = ProductId(productIdToBuy),
            orderId = null,
            quantity = Quantity(1),
            developerPayload = null,
            appUserId = null,
            appUserEmail = null
        )

        try {
            RuStorePayClient.instance.getPurchaseInteractor()
                .purchase(params)
                .addOnSuccessListener { result ->
                    DebugLogBuffer.log(TAG, "Purchase succeeded: $result")
                    _purchaseState.value = PurchaseState.Success
                }
                .addOnFailureListener { error ->
                    if (error is ru.rustore.sdk.pay.model.RuStorePaymentException.ProductPurchaseCancelled) {
                        _purchaseState.value = PurchaseState.Idle
                        Log.d(TAG, "Purchase cancelled")
                        DebugLogBuffer.log(TAG, "Purchase cancelled by user")
                    } else {
                        val errMsg = error.message ?: "Purchase exception"
                        Log.e(TAG, "Purchase exception", error)
                        DebugLogBuffer.log(TAG, "ERROR purchase: $errMsg (${error.javaClass.simpleName})")
                        _purchaseState.value = PurchaseState.Error(errMsg)
                    }
                }
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "RuStorePayClient error"
            Log.e(TAG, "Exception initiating purchase", e)
            DebugLogBuffer.log(TAG, "EXCEPTION purchase: $errMsg (${e.javaClass.simpleName})")
            _purchaseState.value = PurchaseState.Error(errMsg)
        }
    }

    /**
     * Проверяет купленные ранее товары для восстановления покупок (Restore Purchases).
     * Должен вызываться при запуске приложения.
     */
    override fun checkPurchases(onPremiumUnlocked: ((Boolean) -> Unit)) {
        DebugLogBuffer.log(TAG, "Checking previous purchases...")
        try {
            RuStorePayClient.instance.getPurchaseInteractor().getPurchases()
                .addOnSuccessListener { purchases ->
                    val hasPremium = purchases.any { purchase ->
                        if (purchase is ru.rustore.sdk.pay.model.ProductPurchase) {
                            val isTargetId = purchase.productId.value == PRODUCT_ID || purchase.productId.value == ALT_PRODUCT_ID
                            isTargetId && (purchase.status == ProductPurchaseStatus.CONFIRMED || purchase.status == ProductPurchaseStatus.PAID)
                        } else false
                    }
                    DebugLogBuffer.log(TAG, "checkPurchases result: hasPremium=$hasPremium (purchases count=${purchases.size})")
                    onPremiumUnlocked(hasPremium)
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Failed to check purchases", error)
                    DebugLogBuffer.log(TAG, "ERROR checkPurchases: ${error.localizedMessage}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception checking purchases", e)
            DebugLogBuffer.log(TAG, "EXCEPTION checkPurchases: ${e.localizedMessage}")
        }
    }
    
    override fun handleIntent(intent: android.content.Intent) {
        try {
            ru.rustore.sdk.pay.RuStorePayClient.instance.getIntentInteractor().proceedIntent(intent, sdkTheme = ru.rustore.sdk.pay.model.SdkTheme.LIGHT)
        } catch (_: Exception) {}
    }

    override fun resetState() {
        _purchaseState.value = PurchaseState.Idle
    }

    companion object {
        private const val TAG = "RuStoreBilling"
        const val PRODUCT_ID = "premium_unlock"
        const val ALT_PRODUCT_ID = "my1drive_premium_unlock"
    }
}


