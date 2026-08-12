package by.w6.my1drive.billing

import android.content.Context

object RemoteConfigManagerProvider {
    fun getInstance(context: Context): RemoteConfigManager {
        return RuStoreRemoteConfigManager.getInstance(context)
    }

    fun getInstance(): RemoteConfigManager {
        return RuStoreRemoteConfigManager.getInstance()
    }
}
