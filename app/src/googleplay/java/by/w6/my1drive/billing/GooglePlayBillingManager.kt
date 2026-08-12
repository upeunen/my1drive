package by.w6.my1drive.billing

import android.app.Application
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import by.w6.my1drive.utils.DebugLogBuffer

class GooglePlayBillingManager(private val application: Application) : IBillingManager {

    private val _productPriceText = MutableStateFlow<String?>(null)
    override val productPriceText: StateFlow<String?> = _productPriceText.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    override val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    override fun loadProducts() {
        _purchaseState.value = PurchaseState.Loading
        DebugLogBuffer.log(TAG, "Google Play Billing loadProducts stub")
        _purchaseState.value = PurchaseState.Idle
    }

    override fun purchasePremium(targetProductId: String?) {
        _purchaseState.value = PurchaseState.Purchasing
        DebugLogBuffer.log(TAG, "Google Play Billing purchasePremium stub")
        _purchaseState.value = PurchaseState.Idle
    }

    override fun checkPurchases(onPremiumUnlocked: (Boolean) -> Unit) {
        DebugLogBuffer.log(TAG, "Google Play Billing checkPurchases stub")
        onPremiumUnlocked(false)
    }

    override fun resetState() {
        _purchaseState.value = PurchaseState.Idle
    }

    companion object {
        private const val TAG = "GooglePlayBilling"
    }
}
