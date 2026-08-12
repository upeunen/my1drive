package by.w6.my1drive.billing

import android.app.Application

object BillingManagerProvider {
    fun getBillingManager(application: Application): IBillingManager {
        return GooglePlayBillingManager(application)
    }
}
