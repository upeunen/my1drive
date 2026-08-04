package by.w6.my1drive.billing

import android.util.Log
import by.w6.my1drive.My1DriveApplication
import by.w6.my1drive.data.local.LimitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Синглтон для работы с RuStore Remote Config.
 * Хранит загруженные значения как StateFlow с fallback-дефолтами.
 * Использует RemoteConfigClient из My1DriveApplication.
 *
 * API клиента:
 *   client.init()            → Task<Unit>   — инициализация (однократно)
 *   client.getRemoteConfig() → Task<RemoteConfig> — загрузка конфига
 *   remoteConfig.getInt("key")    / .getString("key") / .getBoolean("key")
 */
class RuStoreRemoteConfigManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Публичные StateFlow с fallback-значениями ---

    private val _maxPhotos = MutableStateFlow(LimitRepository.MAX_PHOTOS)
    /** Максимальное кол-во фото для бесплатной версии */
    val maxPhotos: StateFlow<Int> = _maxPhotos.asStateFlow()

    private val _maxVideos = MutableStateFlow(LimitRepository.MAX_VIDEOS)
    /** Максимальное кол-во видео для бесплатной версии */
    val maxVideos: StateFlow<Int> = _maxVideos.asStateFlow()

    private val _freeTrialDays = MutableStateFlow(0)
    /** Кол-во дней бесплатного триала (0 = выключен) */
    val freeTrialDays: StateFlow<Int> = _freeTrialDays.asStateFlow()

    private val _promoCodesJson = MutableStateFlow("")
    /** JSON со списком промокодов. Формат: {"codes":[{"code":"X","days":30}]} */
    val promoCodesJson: StateFlow<String> = _promoCodesJson.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    /** true после первой успешной загрузки конфига */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // --- Загрузка конфига ---

    /**
     * Инициализирует SDK и загружает Remote Config.
     * Безопасно вызывать повторно — повторная инициализация игнорируется SDK.
     * Вызывать из My1DriveApplication.onCreate() или GalleryViewModel.init().
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
                        _isLoaded.value = true

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

        // Ключи параметров в RuStore Console → Remote Config
        const val KEY_MAX_PHOTOS  = "free_max_photos"
        const val KEY_MAX_VIDEOS  = "free_max_videos"
        const val KEY_TRIAL_DAYS  = "free_trial_days"
        const val KEY_PROMO_CODES = "promo_codes_json"

        @Volatile private var instance: RuStoreRemoteConfigManager? = null

        fun getInstance(): RuStoreRemoteConfigManager =
            instance ?: synchronized(this) {
                instance ?: RuStoreRemoteConfigManager().also { instance = it }
            }
    }
}
