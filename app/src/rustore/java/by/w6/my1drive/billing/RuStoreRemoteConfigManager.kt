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
    var remoteConfigClient: ru.rustore.sdk.remoteconfig.RemoteConfigClient? = null

    private val prefs = context.getSharedPreferences("remote_config_meta", Context.MODE_PRIVATE)

    // --- Публичные StateFlow с fallback-значениями ---

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

    /** true = лимиты включены (управляется из RuStore Console) */
    private val _limitsEnabled = MutableStateFlow(false)
    override val limitsEnabled: StateFlow<Boolean> = _limitsEnabled.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // --- Загрузка конфига ---

    override fun fetchConfigIfStale() {
        val lastFetch = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
        val isStale = System.currentTimeMillis() - lastFetch > REFRESH_INTERVAL_MS
        if (isStale || !_isLoaded.value) {
            DebugLogBuffer.log(TAG, "Config is stale (${(System.currentTimeMillis() - lastFetch) / 1000}s ago), refreshing...")
            fetchConfig()
        } else {
            DebugLogBuffer.log(TAG, "Config is fresh, skipping fetch")
        }
    }

    override fun fetchConfig() {
        val client = remoteConfigClient ?: run {
            Log.e(TAG, "RemoteConfigClient not initialized yet")
            return
        }

        DebugLogBuffer.log(TAG, "Initiating RemoteConfig SDK fetch...")
        client.init()
            .addOnSuccessListener {
                DebugLogBuffer.log(TAG, "SDK init success, requesting RemoteConfig...")
                client.getRemoteConfig()
                    .addOnSuccessListener { config ->
                        // Изолированное безопасное чтение каждого параметра
                        _maxPhotos.value = runCatching { config.getInt(KEY_MAX_PHOTOS) }
                            .getOrNull()?.takeIf { it > 0 } ?: LimitRepository.MAX_PHOTOS

                        _maxVideos.value = runCatching { config.getInt(KEY_MAX_VIDEOS) }
                            .getOrNull()?.takeIf { it > 0 } ?: LimitRepository.MAX_VIDEOS

                        _freeTrialDays.value = runCatching { config.getInt(KEY_TRIAL_DAYS) }
                            .getOrDefault(0)

                        _promoCodesJson.value = runCatching { config.getString(KEY_PROMO_CODES) }
                            .getOrDefault("")

                        _announcementJson.value = runCatching { config.getString(KEY_ANNOUNCEMENT_JSON) }
                            .getOrDefault("")

                        _limitsEnabled.value = runCatching { config.getBoolean(KEY_LIMITS_ENABLED) }
                            .getOrDefault(false)

                        _isLoaded.value = true

                        // Сохраняем время успешной загрузки
                        prefs.edit().putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis()).apply()

                        val logMsg = "Config loaded: " +
                                "limitsEnabled=${_limitsEnabled.value}, " +
                                "maxPhotos=${_maxPhotos.value}, " +
                                "maxVideos=${_maxVideos.value}, " +
                                "trialDays=${_freeTrialDays.value}, " +
                                "hasPromo=${_promoCodesJson.value.isNotBlank()}, " +
                                "hasAnnouncement=${_announcementJson.value.isNotBlank()}"

                        Log.d(TAG, logMsg)
                        DebugLogBuffer.log(TAG, logMsg)
                    }
                    .addOnFailureListener { e ->
                        val err = "getRemoteConfig failed: ${e.localizedMessage}. Using fallbacks."
                        Log.w(TAG, err, e)
                        DebugLogBuffer.log(TAG, err)
                        _limitsEnabled.value = true
                        _isLoaded.value = true
                    }
            }
            .addOnFailureListener { e ->
                val err = "SDK init failed: ${e.localizedMessage}. Using fallbacks."
                Log.w(TAG, err, e)
                DebugLogBuffer.log(TAG, err)
                _limitsEnabled.value = true
                _isLoaded.value = true
            }
    }

    companion object {
        private const val TAG = "RemoteConfig"
        private const val REFRESH_INTERVAL_MS = 60 * 60 * 1000L // 1 час

        // Ключи параметров в RuStore Console → Remote Config
        const val KEY_MAX_PHOTOS  = "free_max_photos"
        const val KEY_MAX_VIDEOS  = "free_max_videos"
        const val KEY_TRIAL_DAYS  = "free_trial_days"
        const val KEY_PROMO_CODES = "promo_codes_json"
        const val KEY_LIMITS_ENABLED = "limits_enabled"
        const val KEY_ANNOUNCEMENT_JSON = "announcement_json"

        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"

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
