package by.w6.my1drive.billing

import android.content.Context
import android.util.Log
import by.w6.my1drive.data.local.LimitRepository
import by.w6.my1drive.utils.DebugLogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Менеджер конфигурации, загружающий лимиты, промокоды и объявления с сервера my1drive.com.
 * Лимиты и триал закрепляются для пользователя один раз при первой установке (Grandfathering).
 * Промокоды и объявления регулярно обновляются с сервера.
 */
class ServerRemoteConfigManager private constructor(context: Context) : RemoteConfigManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("server_remote_config_prefs", Context.MODE_PRIVATE)
    private val limitRepository = LimitRepository(context)

    // --- StateFlow со значениями ---

    override val maxPhotos: StateFlow<Int> = limitRepository.userMaxPhotos
    override val maxVideos: StateFlow<Int> = limitRepository.userMaxVideos
    override val freeTrialDays: StateFlow<Int> = limitRepository.userTrialDays

    private val _promoCodesJson = MutableStateFlow(
        prefs.getString(KEY_PROMO_CODES, "") ?: ""
    )
    override val promoCodesJson: StateFlow<String> = _promoCodesJson.asStateFlow()

    private val _announcementJson = MutableStateFlow(
        prefs.getString(KEY_ANNOUNCEMENT_JSON, "") ?: ""
    )
    override val announcementJson: StateFlow<String> = _announcementJson.asStateFlow()

    private val _limitsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LIMITS_ENABLED, true)
    )
    override val limitsEnabled: StateFlow<Boolean> = _limitsEnabled.asStateFlow()

    private val _isLoaded = MutableStateFlow(
        prefs.contains(KEY_LAST_FETCH_TIME)
    )
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    override fun fetchConfigIfStale() {
        val lastFetch = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
        val isStale = (System.currentTimeMillis() - lastFetch) > REFRESH_INTERVAL_MS
        if (isStale || !_isLoaded.value) {
            DebugLogBuffer.log(TAG, "Config is stale (${(System.currentTimeMillis() - lastFetch) / 1000}s ago), refreshing...")
            fetchConfig()
        } else {
            DebugLogBuffer.log(TAG, "Config is fresh, skipping fetch")
        }
    }

    override fun fetchConfig() {
        scope.launch {
            try {
                DebugLogBuffer.log(TAG, "Fetching Remote Config from $CONFIG_URL ...")
                val url = URL(CONFIG_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "My1Drive-Android")
                    setRequestProperty("Accept", "application/json")
                    
                    val savedEtag = prefs.getString(KEY_ETAG, null)
                    if (!savedEtag.isNullOrBlank()) {
                        setRequestProperty("If-None-Match", savedEtag)
                    }
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    DebugLogBuffer.log(TAG, "Config not modified (304). Cache is valid.")
                    prefs.edit().putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis()).apply()
                    _isLoaded.value = true
                    conn.disconnect()
                    return@launch
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val newEtag = conn.getHeaderField("ETag")
                    parseAndApplyConfig(response, newEtag)
                } else {
                    Log.w(TAG, "Failed to fetch config, HTTP code: $responseCode")
                    DebugLogBuffer.log(TAG, "Failed to fetch config, HTTP code: $responseCode")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching Remote Config: ${e.localizedMessage}")
                DebugLogBuffer.log(TAG, "Error fetching Remote Config: ${e.localizedMessage}")
            }
        }
    }

    private fun parseAndApplyConfig(jsonStr: String, etag: String?) {
        try {
            val root = JSONObject(jsonStr)

            val limitsEnabledVal = root.optBoolean("limits_enabled", true)
            val maxPhotosVal = root.optInt("free_max_photos", LimitRepository.MAX_PHOTOS).takeIf { it > 0 } ?: LimitRepository.MAX_PHOTOS
            val maxVideosVal = root.optInt("free_max_videos", LimitRepository.MAX_VIDEOS).takeIf { it > 0 } ?: LimitRepository.MAX_VIDEOS
            val freeTrialDaysVal = root.optInt("free_trial_days", 0)

            val promoCodesVal = if (root.has("promo_codes")) {
                root.get("promo_codes").toString()
            } else ""

            val announcementVal = if (root.has("announcement")) {
                root.get("announcement").toString()
            } else ""

            // Закрепляем лимиты и триал при первом получении конфигурации с сервера
            limitRepository.lockInitialConfig(maxPhotosVal, maxVideosVal, freeTrialDaysVal)

            _limitsEnabled.value = limitsEnabledVal
            _promoCodesJson.value = promoCodesVal
            _announcementJson.value = announcementVal
            _isLoaded.value = true

            prefs.edit()
                .putBoolean(KEY_LIMITS_ENABLED, limitsEnabledVal)
                .putString(KEY_PROMO_CODES, promoCodesVal)
                .putString(KEY_ANNOUNCEMENT_JSON, announcementVal)
                .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                .apply {
                    if (etag != null) putString(KEY_ETAG, etag)
                }
                .apply()

            val logMsg = "Server Config loaded: " +
                    "limitsEnabled=$limitsEnabledVal, " +
                    "maxPhotos=$maxPhotosVal, " +
                    "maxVideos=$maxVideosVal, " +
                    "trialDays=$freeTrialDaysVal, " +
                    "hasPromo=${promoCodesVal.isNotBlank()}, " +
                    "hasAnnouncement=${announcementVal.isNotBlank()}"

            Log.d(TAG, logMsg)
            DebugLogBuffer.log(TAG, logMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server config JSON: ${e.localizedMessage}", e)
            DebugLogBuffer.log(TAG, "Error parsing server config JSON: ${e.localizedMessage}")
        }
    }

    companion object {
        private const val TAG = "ServerRemoteConfig"
        private const val CONFIG_URL = "https://my1drive.com/app-config.php"
        private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L // 15 минут

        private const val KEY_PROMO_CODES = "promo_codes_json"
        private const val KEY_LIMITS_ENABLED = "limits_enabled"
        private const val KEY_ANNOUNCEMENT_JSON = "announcement_json"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
        private const val KEY_ETAG = "config_etag"

        @Volatile private var instance: ServerRemoteConfigManager? = null

        fun getInstance(context: Context): ServerRemoteConfigManager =
            instance ?: synchronized(this) {
                instance ?: ServerRemoteConfigManager(context.applicationContext).also { instance = it }
            }

        fun getInstance(): ServerRemoteConfigManager =
            instance ?: throw IllegalStateException(
                "ServerRemoteConfigManager not initialized. Call getInstance(context) first."
            )
    }
}
