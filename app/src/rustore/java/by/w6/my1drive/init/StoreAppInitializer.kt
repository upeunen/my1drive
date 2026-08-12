package by.w6.my1drive.init

import android.app.Application
import android.content.Intent
import by.w6.my1drive.R
import by.w6.my1drive.billing.RuStoreRemoteConfigManager
import ru.rustore.sdk.remoteconfig.AppId
import ru.rustore.sdk.remoteconfig.RemoteConfigClientBuilder

object StoreAppInitializer : StoreInitializer {
    override fun initApplication(app: Application) {
        val client = RemoteConfigClientBuilder(
            appId = AppId(app.getString(R.string.RUSTORE_REMOTE_CONFIG_APP_ID)),
            context = app
        ).build()
        
        val manager = RuStoreRemoteConfigManager.getInstance(app)
        manager.remoteConfigClient = client
        manager.fetchConfig()
    }

    override fun onMainActivityNewIntent(intent: Intent?) {
        intent?.let {
            ru.rustore.sdk.pay.RuStorePayClient.instance.getIntentInteractor()
                .proceedIntent(it, sdkTheme = ru.rustore.sdk.pay.model.SdkTheme.LIGHT)
        }
    }
}
