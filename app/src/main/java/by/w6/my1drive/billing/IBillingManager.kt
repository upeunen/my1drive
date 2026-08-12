package by.w6.my1drive.billing

import kotlinx.coroutines.flow.StateFlow

sealed class PurchaseState {
    object Idle : PurchaseState()
    object Loading : PurchaseState()
    object Purchasing : PurchaseState()
    object Success : PurchaseState()
    data class Error(val message: String) : PurchaseState()
}

interface IBillingManager {
    val purchaseState: StateFlow<PurchaseState>
    val productPriceText: StateFlow<String?>
    fun loadProducts()
    fun purchasePremium(targetProductId: String? = null)
    fun checkPurchases(onPremiumUnlocked: (Boolean) -> Unit)
    fun resetState()
    fun handleIntent(intent: android.content.Intent) {}
}
