package by.w6.my1drive.billing

import kotlinx.coroutines.flow.StateFlow

interface RemoteConfigManager {
    val maxPhotos: StateFlow<Int>
    val maxVideos: StateFlow<Int>
    val freeTrialDays: StateFlow<Int>
    val promoCodesJson: StateFlow<String>
    val announcementJson: StateFlow<String>
    val limitsEnabled: StateFlow<Boolean>
    val isLoaded: StateFlow<Boolean>

    fun fetchConfigIfStale()
    fun fetchConfig()
}
