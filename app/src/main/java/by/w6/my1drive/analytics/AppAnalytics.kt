package by.w6.my1drive.analytics

object AppAnalytics {
    fun updateTrustLevel(level: Int) {}
    fun logArchiveSessionStart() {}
    fun logArchiveSessionEnd(freedMb: Float, status: String, filesDone: Int) {}
    fun logTabSwitch(tabName: String) {}
    fun logAppBackground() {}
    fun logAppForeground() {}
    fun logPaywallShown() {}
    fun logPurchaseSuccess() {}
    fun logPurchaseCancelled() {}
    fun logPurchaseError(errorMessage: String) {}
    fun logSoftCapTriggered(originalFilesCount: Int) {}
}
