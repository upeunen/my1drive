package by.w6.my1drive.billing

import android.content.Context
import by.w6.my1drive.data.local.LimitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GooglePlayRemoteConfigManager private constructor(context: Context) : RemoteConfigManager {

    private val _maxPhotos = MutableStateFlow(LimitRepository.MAX_PHOTOS)
    override val maxPhotos: StateFlow<Int> = _maxPhotos.asStateFlow()

    private val _maxVideos = MutableStateFlow(LimitRepository.MAX_VIDEOS)
    override val maxVideos: StateFlow<Int> = _maxVideos.asStateFlow()

    private val _freeTrialDays = MutableStateFlow(0)
    override val freeTrialDays: StateFlow<Int> = _freeTrialDays.asStateFlow()

    private val _promoCodesJson = MutableStateFlow("")
    override val promoCodesJson: StateFlow<String> = _promoCodesJson.asStateFlow()

    private val _announcementJson = MutableStateFlow("")
    override val announcementJson: StateFlow<String> = _announcementJson.asStateFlow()

    private val _limitsEnabled = MutableStateFlow(false)
    override val limitsEnabled: StateFlow<Boolean> = _limitsEnabled.asStateFlow()

    private val _isLoaded = MutableStateFlow(true)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    override fun fetchConfigIfStale() {}

    override fun fetchConfig() {}

    companion object {
        @Volatile private var instance: GooglePlayRemoteConfigManager? = null

        fun getInstance(context: Context): GooglePlayRemoteConfigManager =
            instance ?: synchronized(this) {
                instance ?: GooglePlayRemoteConfigManager(context.applicationContext).also { instance = it }
            }

        fun getInstance(): GooglePlayRemoteConfigManager =
            instance ?: throw IllegalStateException(
                "GooglePlayRemoteConfigManager not initialized."
            )
    }
}
