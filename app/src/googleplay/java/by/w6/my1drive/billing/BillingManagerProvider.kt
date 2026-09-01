package by.w6.my1drive.billing

import android.app.Application

object BillingManagerProvider {
    @Volatile
    private var instance: GooglePlayBillingManager? = null

    fun getBillingManager(application: Application): IBillingManager {
        return instance ?: synchronized(this) {
            instance ?: GooglePlayBillingManager(application).also { instance = it }
        }
    }
}

