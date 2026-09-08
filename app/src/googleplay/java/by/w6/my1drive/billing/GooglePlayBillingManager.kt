package by.w6.my1drive.billing

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import by.w6.my1drive.data.local.LimitRepository
import by.w6.my1drive.utils.DebugLogBuffer
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class GooglePlayBillingManager(private val application: Application) : IBillingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val limitRepository by lazy { LimitRepository(application) }

    private val _productPriceText = MutableStateFlow<String?>(null)
    override val productPriceText: StateFlow<String?> = _productPriceText.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    override val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private val _premiumProductDetails = MutableStateFlow<ProductDetails?>(null)

    private var currentActivityRef: WeakReference<Activity>? = null
    private var isConnecting = false
    private val pendingConnectionActions = mutableListOf<() -> Unit>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        handlePurchasesUpdated(billingResult, purchases)
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(application)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                currentActivityRef = WeakReference(activity)
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                }
            }
        })
    }

    private fun ensureConnected(onReady: () -> Unit, onError: ((String) -> Unit)? = null) {
        if (billingClient.isReady) {
            onReady()
            return
        }

        pendingConnectionActions.add(onReady)
        if (isConnecting) return

        isConnecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                isConnecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    DebugLogBuffer.log(TAG, "BillingClient connected successfully")
                    val actions = pendingConnectionActions.toList()
                    pendingConnectionActions.clear()
                    actions.forEach { it.invoke() }
                } else {
                    val errMsg = "Billing connection error: ${billingResult.debugMessage} (${billingResult.responseCode})"
                    Log.e(TAG, errMsg)
                    DebugLogBuffer.log(TAG, errMsg)
                    pendingConnectionActions.clear()
                    onError?.invoke(errMsg)
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
                DebugLogBuffer.log(TAG, "BillingClient service disconnected")
            }
        })
    }

    override fun loadProducts() {
        _purchaseState.value = PurchaseState.Loading
        DebugLogBuffer.log(TAG, "Loading products: $PRODUCT_ID, $ALT_PRODUCT_ID")

        ensureConnected(
            onReady = {
                val productList = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ALT_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )

                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build()

                billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val productDetailsList = productDetailsResult.productDetailsList ?: emptyList()
                        val product = productDetailsList.find {
                            it.productId == PRODUCT_ID || it.productId == ALT_PRODUCT_ID
                        }
                        _premiumProductDetails.value = product
                        val formattedPrice = product?.oneTimePurchaseOfferDetails?.formattedPrice
                        _productPriceText.value = formattedPrice
                        _purchaseState.value = PurchaseState.Idle
                        val msg = "Product loaded: ${product?.title} (price=$formattedPrice, id=${product?.productId})"
                        Log.d(TAG, msg)
                        DebugLogBuffer.log(TAG, msg)
                    } else {
                        val errMsg = "Failed to query products: ${billingResult.debugMessage} (${billingResult.responseCode})"
                        Log.e(TAG, errMsg)
                        DebugLogBuffer.log(TAG, errMsg)
                        _purchaseState.value = PurchaseState.Error(errMsg)
                    }
                }
            },
            onError = { errMsg ->
                _purchaseState.value = PurchaseState.Error(errMsg)
            }
        )
    }

    override fun purchasePremium(targetProductId: String?) {
        val activity = currentActivityRef?.get()
        if (activity == null) {
            val errMsg = "Activity context is unavailable for purchase flow"
            DebugLogBuffer.log(TAG, "ERROR purchasePremium: $errMsg")
            _purchaseState.value = PurchaseState.Error(errMsg)
            return
        }

        _purchaseState.value = PurchaseState.Purchasing
        DebugLogBuffer.log(TAG, "Initiating purchase flow...")

        ensureConnected(
            onReady = {
                val details = _premiumProductDetails.value
                if (details != null) {
                    launchBillingFlow(activity, details)
                } else {
                    // Try to load product details first, then launch
                    val productList = listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(targetProductId ?: PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                    val params = QueryProductDetailsParams.newBuilder()
                        .setProductList(productList)
                        .build()

                    billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                        val productDetailsList = productDetailsResult.productDetailsList ?: emptyList()
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                            val product = productDetailsList.first()
                            _premiumProductDetails.value = product
                            _productPriceText.value = product.oneTimePurchaseOfferDetails?.formattedPrice
                            launchBillingFlow(activity, product)
                        } else {
                            val errMsg = "Product details not found (${billingResult.responseCode})"
                            DebugLogBuffer.log(TAG, "ERROR purchase: $errMsg")
                            _purchaseState.value = PurchaseState.Error(errMsg)
                        }
                    }
                }
            },
            onError = { errMsg ->
                _purchaseState.value = PurchaseState.Error(errMsg)
            }
        )
    }

    private fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        DebugLogBuffer.log(TAG, "launchBillingFlow result: ${result.responseCode} (${result.debugMessage})")
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseState.value = PurchaseState.Error("Launch billing failed: ${result.debugMessage}")
        }
    }

    private fun handlePurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        DebugLogBuffer.log(TAG, "handlePurchasesUpdated: code=${billingResult.responseCode}, count=${purchases?.size ?: 0}")
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    _purchaseState.value = PurchaseState.Idle
                    return
                }
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                DebugLogBuffer.log(TAG, "Purchase cancelled by user")
                _purchaseState.value = PurchaseState.Idle
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                DebugLogBuffer.log(TAG, "Item already owned, granting premium")
                limitRepository.isPremiumUnlocked = true
                _purchaseState.value = PurchaseState.Success
            }
            else -> {
                val errMsg = billingResult.debugMessage.ifBlank { "Purchase error code: ${billingResult.responseCode}" }
                DebugLogBuffer.log(TAG, "ERROR handlePurchasesUpdated: $errMsg")
                _purchaseState.value = PurchaseState.Error(errMsg)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        DebugLogBuffer.log(TAG, "Processing purchase: ${purchase.orderId}, state=${purchase.purchaseState}")
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                limitRepository.isPremiumUnlocked = true
                _purchaseState.value = PurchaseState.Success

                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient.acknowledgePurchase(ackParams) { ackResult ->
                        DebugLogBuffer.log(TAG, "AcknowledgePurchase result: ${ackResult.responseCode} (${ackResult.debugMessage})")
                    }
                }
            }
            Purchase.PurchaseState.PENDING -> {
                DebugLogBuffer.log(TAG, "Purchase is pending confirmation")
                _purchaseState.value = PurchaseState.Purchasing
            }
            else -> {
                DebugLogBuffer.log(TAG, "Purchase state is ${purchase.purchaseState}")
                _purchaseState.value = PurchaseState.Idle
            }
        }
    }

    override fun checkPurchases(onPremiumUnlocked: (Boolean) -> Unit) {
        DebugLogBuffer.log(TAG, "Checking previous Google Play purchases...")
        ensureConnected(
            onReady = {
                val queryParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

                billingClient.queryPurchasesAsync(queryParams) { billingResult, purchasesList ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val hasPremium = purchasesList.any { purchase ->
                            val matches = purchase.products.contains(PRODUCT_ID) || purchase.products.contains(ALT_PRODUCT_ID)
                            matches && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        }
                        DebugLogBuffer.log(TAG, "checkPurchases result: hasPremium=$hasPremium (purchases=${purchasesList.size})")
                        if (hasPremium) {
                            limitRepository.isPremiumUnlocked = true
                            // Acknowledge unacknowledged purchases if needed
                            purchasesList.forEach { purchase ->
                                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                                        .setPurchaseToken(purchase.purchaseToken)
                                        .build()
                                    billingClient.acknowledgePurchase(ackParams) { ackRes ->
                                        DebugLogBuffer.log(TAG, "Auto-acknowledge on check: ${ackRes.responseCode}")
                                    }
                                }
                            }
                        }
                        scope.launch {
                            onPremiumUnlocked(hasPremium)
                        }
                    } else {
                        val errMsg = "Failed to query purchases: ${billingResult.debugMessage} (${billingResult.responseCode})"
                        Log.e(TAG, errMsg)
                        DebugLogBuffer.log(TAG, "ERROR checkPurchases: $errMsg")
                        scope.launch {
                            onPremiumUnlocked(false)
                        }
                    }
                }
            },
            onError = {
                scope.launch {
                    onPremiumUnlocked(false)
                }
            }
        )
    }

    override fun resetState() {
        _purchaseState.value = PurchaseState.Idle
    }

    companion object {
        private const val TAG = "GooglePlayBilling"
        const val PRODUCT_ID = "premium_unlock"
        const val ALT_PRODUCT_ID = "my1drive_premium_unlock"
    }
}
