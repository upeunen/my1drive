package by.w6.my1drive.billing

import android.content.Context
import android.util.Log
import by.w6.my1drive.My1DriveApplication
import by.w6.my1drive.data.local.LimitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Синглтон для работы с RuStore Remote Config.
 * Хранит загруженные значения как StateFlow с fallback-дефолтами.
 *
 * Стратегия обновления: fetchConfigIfStale() проверяет TTL = 1 час.
 * fetchConfig() — принудительное обновление (при старте приложения).
 *
 * API клиента:
 *   client.init()            → Task<Unit>
 *   client.getRemoteConfig() → Task<RemoteConfig>
 */
class RuStoreRemoteConfigManager private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("remote_config_meta", Context.MODE_PRIVATE)

    // --- Публичные StateFlow с fallback-значениями ---

    private val _maxPhotos = MutableStateFlow(LimitRepository.MAX_PHOTOS)
    val maxPhotos: StateFlow<Int> = _maxPhotos.asStateFlow()

    private val _maxVideos = MutableStateFlow(LimitRepository.MAX_VIDEOS)
    val maxVideos: StateFlow<Int> = _maxVideos.asStateFlow()

    private val _freeTrialDays = MutableStateFlow(0)
    val freeTrialDays: StateFlow<Int> = _freeTrialDays.asStateFlow()

    private val _promoCodesJson = MutableStateFlow("")
    val promoCodesJson: StateFlow<String> = _promoCodesJson.asStateFlow()

    /** true = лимиты включены (управляется из RuStore Console) */
    private val _limitsEnabled = MutableStateFlow(false) // false пока конфиг не загрузился
    val limitsEnabled: StateFlow<Boolean> = _limitsEnabled.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // --- Загрузка конфига ---

    /**
     * Обновляет конфиг только если прошёл REFRESH_INTERVAL с последней загрузки.
     * Вызывать при каждом foreground-старте Activity.
     */
    fun fetchConfigIfStale() {
        val lastFetch = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
        val isStale = System.currentTimeMillis() - lastFetch > REFRESH_INTERVAL_MS
        if (isStale || !_isLoaded.value) {
            Log.d(TAG, "Config is stale (${(System.currentTimeMillis() - lastFetch) / 1000}s ago), refreshing...")
            fetchConfig()
        } else {
            Log.d(TAG, "Config is fresh, skipping fetch")
        }
    }

    /**
     * Принудительно загружает Remote Config.
     * Вызывать из Application.onCreate().
     */
    fun fetchConfig() {
        val client = try {
            My1DriveApplication.remoteConfigClient
        } catch (e: UninitializedPropertyAccessException) {
            Log.e(TAG, "RemoteConfigClient not initialized yet", e)
            return
        }

        client.init()
            .addOnSuccessListener {
                Log.d(TAG, "SDK init success, fetching config...")
                client.getRemoteConfig()
                    .addOnSuccessListener { config ->
                        _maxPhotos.value = config.getInt(KEY_MAX_PHOTOS)
                            .takeIf { it > 0 } ?: LimitRepository.MAX_PHOTOS
                        _maxVideos.value = config.getInt(KEY_MAX_VIDEOS)
                            .takeIf { it > 0 } ?: LimitRepository.MAX_VIDEOS
                        _freeTrialDays.value = config.getInt(KEY_TRIAL_DAYS)
                        _promoCodesJson.value = config.getString(KEY_PROMO_CODES)
                        _limitsEnabled.value = config.getBoolean(KEY_LIMITS_ENABLED)
                        _isLoaded.value = true

                        // Сохраняем время успешной загрузки
                        prefs.edit().putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis()).apply()

                        Log.d(TAG, "Config loaded: " +
                            "maxPhotos=${_maxPhotos.value}, " +
                            "maxVideos=${_maxVideos.value}, " +
                            "trialDays=${_freeTrialDays.value}, " +
                            "promoCodes=${_promoCodesJson.value.take(80)}"
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "getRemoteConfig failed, using defaults", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "SDK init failed, using defaults", e)
            }
    }

    companion object {
        private const val TAG = "RemoteConfigManager"
        private const val REFRESH_INTERVAL_MS = 60 * 60 * 1000L // 1 час

        // Ключи параметров в RuStore Console → Remote Config
        const val KEY_MAX_PHOTOS  = "free_max_photos"
        const val KEY_MAX_VIDEOS  = "free_max_videos"
        const val KEY_TRIAL_DAYS  = "free_trial_days"
        const val KEY_PROMO_CODES = "promo_codes_json"
        const val KEY_LIMITS_ENABLED = "limits_enabled"

        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"

        @Volatile private var instance: RuStoreRemoteConfigManager? = null

        fun getInstance(context: Context): RuStoreRemoteConfigManager =
            instance ?: synchronized(this) {
                instance ?: RuStoreRemoteConfigManager(context.applicationContext).also { instance = it }
            }

        /** Для обратной совместимости — вызов из ViewModel без context */
        fun getInstance(): RuStoreRemoteConfigManager =
            instance ?: throw IllegalStateException(
                "RuStoreRemoteConfigManager not initialized. Call getInstance(context) first."
            )
    }
}
