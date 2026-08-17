package by.w6.my1drive.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class LimitRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = try {
        createEncryptedPrefs(context, masterKey)
    } catch (e: Exception) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.deleteSharedPreferences("limits_prefs_secured")
            } else {
                context.getSharedPreferences("limits_prefs_secured", Context.MODE_PRIVATE).edit().clear().apply()
                val dir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                val file = java.io.File(dir, "limits_prefs_secured.xml")
                if (file.exists()) file.delete()
            }
            createEncryptedPrefs(context, masterKey)
        } catch (ignored: Exception) {
            context.getSharedPreferences("limits_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(context: Context, masterKey: MasterKey): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            "limits_prefs_secured",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Время первого запуска приложения */
    val firstLaunchTime: Long = runCatching {
        val saved = prefs.getLong(KEY_FIRST_LAUNCH_TIME, 0L)
        if (saved == 0L) {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_LAUNCH_TIME, now).apply()
            now
        } else {
            saved
        }
    }.getOrDefault(System.currentTimeMillis())

    // --- Закрепленные первоначальные лимиты пользователя (Grandfathering) ---

    private val _isConfigLocked = MutableStateFlow(
        runCatching { prefs.getBoolean(KEY_IS_CONFIG_LOCKED, false) }.getOrDefault(false)
    )
    val isConfigLocked: StateFlow<Boolean> = _isConfigLocked.asStateFlow()

    private val _userMaxPhotos = MutableStateFlow(
        runCatching { prefs.getInt(KEY_USER_MAX_PHOTOS, MAX_PHOTOS) }.getOrDefault(MAX_PHOTOS)
    )
    val userMaxPhotos: StateFlow<Int> = _userMaxPhotos.asStateFlow()

    private val _userMaxVideos = MutableStateFlow(
        runCatching { prefs.getInt(KEY_USER_MAX_VIDEOS, MAX_VIDEOS) }.getOrDefault(MAX_VIDEOS)
    )
    val userMaxVideos: StateFlow<Int> = _userMaxVideos.asStateFlow()

    private val _userTrialDays = MutableStateFlow(
        runCatching { prefs.getInt(KEY_USER_TRIAL_DAYS, 0) }.getOrDefault(0)
    )
    val userTrialDays: StateFlow<Int> = _userTrialDays.asStateFlow()

    private val _userTrialEndTime = MutableStateFlow(
        runCatching { prefs.getLong(KEY_USER_TRIAL_END_TIME, 0L) }.getOrDefault(0L)
    )
    val userTrialEndTime: StateFlow<Long> = _userTrialEndTime.asStateFlow()

    /**
     * Фиксирует первоначальные лимиты и триал ровно один раз при первом получении конфигурации.
     * Последующие изменения в админке не перезаписывают данные у существующего пользователя.
     */
    fun lockInitialConfig(maxPhotos: Int, maxVideos: Int, trialDays: Int) {
        if (_isConfigLocked.value) return
        val now = System.currentTimeMillis()
        val trialEnd = if (trialDays > 0) now + trialDays.toLong() * 24 * 60 * 60 * 1000L else 0L
        runCatching {
            prefs.edit()
                .putBoolean(KEY_IS_CONFIG_LOCKED, true)
                .putInt(KEY_USER_MAX_PHOTOS, maxPhotos)
                .putInt(KEY_USER_MAX_VIDEOS, maxVideos)
                .putInt(KEY_USER_TRIAL_DAYS, trialDays)
                .putLong(KEY_USER_TRIAL_END_TIME, trialEnd)
                .apply()
        }
        _isConfigLocked.value = true
        _userMaxPhotos.value = maxPhotos
        _userMaxVideos.value = maxVideos
        _userTrialDays.value = trialDays
        _userTrialEndTime.value = trialEnd
    }

    // --- Счётчики архивации ---

    private val _photosArchivedCount = MutableStateFlow(runCatching { prefs.getInt(KEY_PHOTOS_COUNT, 0) }.getOrDefault(0))
    val photosArchivedCountFlow: StateFlow<Int> = _photosArchivedCount.asStateFlow()

    private val _videosArchivedCount = MutableStateFlow(runCatching { prefs.getInt(KEY_VIDEOS_COUNT, 0) }.getOrDefault(0))
    val videosArchivedCountFlow: StateFlow<Int> = _videosArchivedCount.asStateFlow()

    private val _isPremiumUnlocked = MutableStateFlow(runCatching { prefs.getBoolean(KEY_PREMIUM, false) }.getOrDefault(false))
    val isPremiumUnlockedFlow: StateFlow<Boolean> = _isPremiumUnlocked.asStateFlow()

    var photosArchivedCount: Int
        get() = runCatching { prefs.getInt(KEY_PHOTOS_COUNT, 0) }.getOrDefault(_photosArchivedCount.value)
        set(value) {
            runCatching { prefs.edit().putInt(KEY_PHOTOS_COUNT, value).apply() }
            _photosArchivedCount.value = value
        }

    var videosArchivedCount: Int
        get() = runCatching { prefs.getInt(KEY_VIDEOS_COUNT, 0) }.getOrDefault(_videosArchivedCount.value)
        set(value) {
            runCatching { prefs.edit().putInt(KEY_VIDEOS_COUNT, value).apply() }
            _videosArchivedCount.value = value
        }

    var isPremiumUnlocked: Boolean
        get() = runCatching { prefs.getBoolean(KEY_PREMIUM, false) }.getOrDefault(_isPremiumUnlocked.value)
        set(value) {
            runCatching { prefs.edit().putBoolean(KEY_PREMIUM, value).apply() }
            _isPremiumUnlocked.value = value
        }

    var trustLevel: Int
        get() = runCatching { prefs.getInt(KEY_TRUST_LEVEL, 0) }.getOrDefault(0)
        set(value) {
            runCatching { prefs.edit().putInt(KEY_TRUST_LEVEL, value).apply() }
        }

    // --- Промокод ---

    /** Код последнего активированного промокода (или null) */
    var appliedPromoCode: String?
        get() = runCatching { prefs.getString(KEY_PROMO_CODE, null) }.getOrNull()
        set(value) {
            runCatching { prefs.edit().putString(KEY_PROMO_CODE, value).apply() }
        }

    /** Timestamp (мс) до которого промокод активен. 0 = неактивен */
    var promoUntilTimestamp: Long
        get() = runCatching { prefs.getLong(KEY_PROMO_UNTIL, 0L) }.getOrDefault(0L)
        set(value) {
            runCatching { prefs.edit().putLong(KEY_PROMO_UNTIL, value).apply() }
            _isPromoActive.value = value > System.currentTimeMillis()
        }

    private val _isPromoActive = MutableStateFlow(
        runCatching { prefs.getLong(KEY_PROMO_UNTIL, 0L) > System.currentTimeMillis() }.getOrDefault(false)
    )
    /** true пока промокод действует */
    val isPromoActiveFlow: StateFlow<Boolean> = _isPromoActive.asStateFlow()

    /** Проверка: промокод ещё не истёк */
    fun isPromoActive(): Boolean = promoUntilTimestamp > System.currentTimeMillis()

    // --- Триал ---

    /**
     * Проверка: бесплатный триал ещё активен.
     */
    fun isTrialActive(): Boolean {
        val end = _userTrialEndTime.value
        return end > 0L && System.currentTimeMillis() < end
    }

    fun isTrialActive(trialDays: Int): Boolean {
        if (!_isConfigLocked.value) {
            if (trialDays <= 0) return false
            val trialEndTime = firstLaunchTime + trialDays.toLong() * 24 * 60 * 60 * 1000
            return System.currentTimeMillis() < trialEndTime
        }
        return isTrialActive()
    }

    /**
     * Возвращает оставшееся количество дней триала.
     */
    fun getRemainingTrialDays(): Int {
        val end = _userTrialEndTime.value
        if (end <= 0L) return 0
        val diffMs = end - System.currentTimeMillis()
        if (diffMs <= 0) return 0
        return ((diffMs + 86399999L) / 86400000L).toInt()
    }

    fun getRemainingTrialDays(trialDays: Int): Int {
        if (!_isConfigLocked.value) {
            if (trialDays <= 0) return 0
            val trialEndTime = firstLaunchTime + trialDays.toLong() * 24 * 60 * 60 * 1000
            val diffMs = trialEndTime - System.currentTimeMillis()
            if (diffMs <= 0) return 0
            return ((diffMs + 86399999L) / 86400000L).toInt()
        }
        return getRemainingTrialDays()
    }

    /** ID последнего показанного дистанционного объявления */
    var lastSeenAnnouncementId: String?
        get() = runCatching { prefs.getString(KEY_LAST_SEEN_ANNOUNCEMENT, null) }.getOrNull()
        set(value) {
            runCatching { prefs.edit().putString(KEY_LAST_SEEN_ANNOUNCEMENT, value).apply() }
        }

    /**
     * Главный метод: нужно ли применять лимиты к пользователю?
     * Возвращает false (лимиты НЕ применяются) если:
     *   - куплен Premium
     *   - активен бесплатный триал
     *   - введён действующий промокод
     */
    fun shouldApplyLimits(trialDays: Int = 0): Boolean {
        if (isPremiumUnlocked) return false
        if (isTrialActive(trialDays)) return false
        if (isPromoActive()) return false
        return true
    }

    companion object {
        /** Fallback-лимиты (используются если Remote Config недоступен) */
        const val MAX_PHOTOS = 100
        const val MAX_VIDEOS = 5

        private const val KEY_FIRST_LAUNCH_TIME = "first_launch_time"
        private const val KEY_IS_CONFIG_LOCKED = "is_config_locked"
        private const val KEY_USER_MAX_PHOTOS = "user_max_photos"
        private const val KEY_USER_MAX_VIDEOS = "user_max_videos"
        private const val KEY_USER_TRIAL_DAYS = "user_trial_days"
        private const val KEY_USER_TRIAL_END_TIME = "user_trial_end_time"

        private const val KEY_PHOTOS_COUNT = "photos_archived_count"
        private const val KEY_VIDEOS_COUNT = "videos_archived_count"
        private const val KEY_PREMIUM = "is_premium_unlocked"
        private const val KEY_TRUST_LEVEL = "trust_level"
        private const val KEY_PROMO_CODE = "applied_promo_code"
        private const val KEY_PROMO_UNTIL = "promo_until_timestamp"
        private const val KEY_LAST_SEEN_ANNOUNCEMENT = "last_seen_announcement_id"
    }
}
