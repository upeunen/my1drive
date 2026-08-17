package by.w6.my1drive.init

import android.app.Application
import android.content.Intent
import by.w6.my1drive.R
import by.w6.my1drive.billing.RuStoreRemoteConfigManager

object StoreAppInitializer : StoreInitializer {
    override fun initApplication(app: Application) {
        runCatching {
            RuStoreRemoteConfigManager.getInstance(app).fetchConfig()
        }.onFailure { e ->
            android.util.Log.w("StoreAppInitializer", "Failed to init remote config: ${e.localizedMessage}")
        }
    }

    override fun onMainActivityNewIntent(intent: Intent?) {
        intent?.let {
            runCatching {
                ru.rustore.sdk.pay.RuStorePayClient.instance.getIntentInteractor()
                    .proceedIntent(it, sdkTheme = ru.rustore.sdk.pay.model.SdkTheme.LIGHT)
            }
        }
    }
}
