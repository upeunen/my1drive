package by.w6.my1drive.billing

import android.content.Context
import android.util.Log
import by.w6.my1drive.My1DriveApplication
import by.w6.my1drive.data.local.LimitRepository
import by.w6.my1drive.utils.DebugLogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Синглтон для работы с RuStore Remote Config.
 * Хранит загруженные значения как StateFlow с fallback-дефолтами.
 * Каждый параметр считывается изолированно через runCatching, чтобы сбой одного ключа не ломал остальные.
 */
class RuStoreRemoteConfigManager private constructor(context: Context) : RemoteConfigManager {
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
        @Volatile private var instance: RuStoreRemoteConfigManager? = null

        fun getInstance(context: Context): RuStoreRemoteConfigManager =
            instance ?: synchronized(this) {
                instance ?: RuStoreRemoteConfigManager(context.applicationContext).also { instance = it }
            }

        fun getInstance(): RuStoreRemoteConfigManager =
            instance ?: throw IllegalStateException(
                "RuStoreRemoteConfigManager not initialized. Call getInstance(context) first."
            )
    }
}
