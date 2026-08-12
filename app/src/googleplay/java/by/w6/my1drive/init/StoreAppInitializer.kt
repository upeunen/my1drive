package by.w6.my1drive.init

import android.app.Application
import android.content.Intent
import by.w6.my1drive.billing.GooglePlayRemoteConfigManager

object StoreAppInitializer : StoreInitializer {
    override fun initApplication(app: Application) {
        GooglePlayRemoteConfigManager.getInstance(app).fetchConfig()
    }

    override fun onMainActivityNewIntent(intent: Intent?) {
        // No-op for Google Play
    }
}
