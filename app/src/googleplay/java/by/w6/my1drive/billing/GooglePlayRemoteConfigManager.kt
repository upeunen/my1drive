package by.w6.my1drive.billing

import android.content.Context
import by.w6.my1drive.data.local.LimitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GooglePlayRemoteConfigManager private constructor(context: Context) : RemoteConfigManager {

    private val serverManager = ServerRemoteConfigManager.getInstance(context)

    override val maxPhotos: StateFlow<Int> = serverManager.maxPhotos
    override val maxVideos: StateFlow<Int> = serverManager.maxVideos
    override val freeTrialDays: StateFlow<Int> = serverManager.freeTrialDays
    override val promoCodesJson: StateFlow<String> = serverManager.promoCodesJson
    override val announcementJson: StateFlow<String> = serverManager.announcementJson
    override val limitsEnabled: StateFlow<Boolean> = serverManager.limitsEnabled
    override val isLoaded: StateFlow<Boolean> = serverManager.isLoaded

    override fun fetchConfigIfStale() {
        serverManager.fetchConfigIfStale()
    }

    override fun fetchConfig() {
        serverManager.fetchConfig()
    }

    companion object {
        @Volatile private var instance: GooglePlayRemoteConfigManager? = null

        fun getInstance(context: Context): GooglePlayRemoteConfigManager =
            instance ?: synchronized(this) {
                instance ?: GooglePlayRemoteConfigManager(context.applicationContext).also { instance = it }
            }

        fun getInstance(): GooglePlayRemoteConfigManager =
            instance ?: throw IllegalStateException(
                "GooglePlayRemoteConfigManager not initialized. Call getInstance(context) first."
            )
    }
}
