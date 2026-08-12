package by.w6.my1drive.billing

import android.content.Context

object RemoteConfigManagerProvider {
    fun getInstance(context: Context): RemoteConfigManager {
        return GooglePlayRemoteConfigManager.getInstance(context)
    }

    fun getInstance(): RemoteConfigManager {
        return GooglePlayRemoteConfigManager.getInstance()
    }
}
