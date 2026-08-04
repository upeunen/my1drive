package by.w6.my1drive

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import ru.rustore.sdk.remoteconfig.AppId
import ru.rustore.sdk.remoteconfig.RemoteConfigClient
import ru.rustore.sdk.remoteconfig.RemoteConfigClientBuilder
import by.w6.my1drive.billing.RuStoreRemoteConfigManager

class My1DriveApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ВАЖНО: Замените на ваш реальный API-ключ от AppMetrica (имеет формат UUID)
        val config = AppMetricaConfig.newConfigBuilder("00000000-0000-0000-0000-000000000000").build()
        AppMetrica.activate(this, config)

        // RuStore Remote Config — инициализация клиента
        remoteConfigClient = RemoteConfigClientBuilder(
            appId = AppId(getString(R.string.CONSOLE_APPLICATION_ID)),
            context = this
        ).build()
        // Запускаем загрузку конфига в фоне сразу при старте
        RuStoreRemoteConfigManager.getInstance().fetchConfig()
    }

    companion object {
        lateinit var remoteConfigClient: RemoteConfigClient
            private set
    }
}
