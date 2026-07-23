package by.w6.my1drive.analytics

// TODO: Убедитесь, что SDK AppMetrica подключен в build.gradle.kts
// implementation("io.appmetrica.analytics:analytics:6.0.0")

import io.appmetrica.analytics.AppMetrica

object AppAnalytics {

    fun updateTrustLevel(level: Int) {
        // Обновляем trust_level, можно использовать profile attributes или просто отправить событие
        val params = mapOf("level" to level)
        AppMetrica.reportEvent("update_trust_level", params.toMap())
    }

    fun logArchiveSessionStart() {
        AppMetrica.reportEvent("archive_session_start")
    }

    fun logArchiveSessionEnd(freedMb: Float, status: String, filesDone: Int) {
        val params = mapOf(
            "freed_mb" to freedMb,
            "status" to status,
            "files_done" to filesDone
        )
        AppMetrica.reportEvent("archive_session_end", params.toMap())
    }

    fun logTabSwitch(tabName: String) {
        val params = mapOf("tab_name" to tabName)
        AppMetrica.reportEvent("tab_switch", params.toMap())
    }

    fun logAppBackground() {
        AppMetrica.reportEvent("app_background")
    }

    fun logAppForeground() {
        AppMetrica.reportEvent("app_foreground")
    }

    fun logPaywallShown() {
        AppMetrica.reportEvent("paywall_shown")
    }

    fun logPurchaseSuccess() {
        AppMetrica.reportEvent("purchase_success")
    }
}
