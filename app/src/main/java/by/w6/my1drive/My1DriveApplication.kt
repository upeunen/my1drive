package by.w6.my1drive

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

class My1DriveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // ВАЖНО: Замените на ваш реальный API-ключ от AppMetrica (имеет формат UUID)
        val config = AppMetricaConfig.newConfigBuilder("00000000-0000-0000-0000-000000000000").build()
        AppMetrica.activate(this, config)
    }
}
