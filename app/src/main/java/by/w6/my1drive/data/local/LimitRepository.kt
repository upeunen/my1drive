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
        } catch (ignored: Exception) {}
        createEncryptedPrefs(context, masterKey)
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

    /** Время первой установки — кешируем один раз, дорогой вызов */
    private val firstInstallTime: Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(0L)

    // --- Счётчики архивации ---

    private val _photosArchivedCount = MutableStateFlow(prefs.getInt(KEY_PHOTOS_COUNT, 0))
    val photosArchivedCountFlow: StateFlow<Int> = _photosArchivedCount.asStateFlow()

    private val _videosArchivedCount = MutableStateFlow(prefs.getInt(KEY_VIDEOS_COUNT, 0))
    val videosArchivedCountFlow: StateFlow<Int> = _videosArchivedCount.asStateFlow()

    private val _isPremiumUnlocked = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, false))
    val isPremiumUnlockedFlow: StateFlow<Boolean> = _isPremiumUnlocked.asStateFlow()

    var photosArchivedCount: Int
        get() = prefs.getInt(KEY_PHOTOS_COUNT, 0)
        set(value) {
            prefs.edit().putInt(KEY_PHOTOS_COUNT, value).apply()
            _photosArchivedCount.value = value
        }

    var videosArchivedCount: Int
        get() = prefs.getInt(KEY_VIDEOS_COUNT, 0)
        set(value) {
            prefs.edit().putInt(KEY_VIDEOS_COUNT, value).apply()
            _videosArchivedCount.value = value
        }

    var isPremiumUnlocked: Boolean
        get() = prefs.getBoolean(KEY_PREMIUM, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PREMIUM, value).apply()
            _isPremiumUnlocked.value = value
        }

    var trustLevel: Int
        get() = prefs.getInt(KEY_TRUST_LEVEL, 0)
        set(value) = prefs.edit().putInt(KEY_TRUST_LEVEL, value).apply()

    // --- Промокод ---

    /** Код последнего активированного промокода (или null) */
    var appliedPromoCode: String?
        get() = prefs.getString(KEY_PROMO_CODE, null)
        set(value) = prefs.edit().putString(KEY_PROMO_CODE, value).apply()

    /** Timestamp (мс) до которого промокод активен. 0 = неактивен */
    var promoUntilTimestamp: Long
        get() = prefs.getLong(KEY_PROMO_UNTIL, 0L)
        set(value) {
            prefs.edit().putLong(KEY_PROMO_UNTIL, value).apply()
            _isPromoActive.value = value > System.currentTimeMillis()
        }

    private val _isPromoActive = MutableStateFlow(
        prefs.getLong(KEY_PROMO_UNTIL, 0L) > System.currentTimeMillis()
    )
    /** true пока промокод действует */
    val isPromoActiveFlow: StateFlow<Boolean> = _isPromoActive.asStateFlow()

    /** Проверка: промокод ещё не истёк */
    fun isPromoActive(): Boolean = promoUntilTimestamp > System.currentTimeMillis()

    // --- Триал ---

    /**
     * Проверка: бесплатный триал ещё активен.
     * @param trialDays кол-во дней триала из Remote Config (0 = выключен)
     */
    /**
     * Проверка: бесплатный триал ещё активен.
     * @param trialDays кол-во дней триала из Remote Config (0 = выключен)
     */
    fun isTrialActive(trialDays: Int): Boolean {
        if (trialDays <= 0) return false
        if (firstInstallTime == 0L) return false
        val trialEndTime = firstInstallTime + trialDays.toLong() * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() < trialEndTime
    }

    /**
     * Возвращает оставшееся количество дней триала.
     */
    fun getRemainingTrialDays(trialDays: Int): Int {
        if (trialDays <= 0 || firstInstallTime == 0L) return 0
        val trialEndTime = firstInstallTime + trialDays.toLong() * 24 * 60 * 60 * 1000
        val diffMs = trialEndTime - System.currentTimeMillis()
        if (diffMs <= 0) return 0
        return ((diffMs + 86399999L) / 86400000L).toInt()
    }

    /** ID последнего показанного дистанционного объявления */
    var lastSeenAnnouncementId: String?
        get() = prefs.getString(KEY_LAST_SEEN_ANNOUNCEMENT, null)
        set(value) = prefs.edit().putString(KEY_LAST_SEEN_ANNOUNCEMENT, value).apply()

    /**
     * Главный метод: нужно ли применять лимиты к пользователю?
     * Возвращает false (лимиты НЕ применяются) если:
     *   - куплен Premium
     *   - активен бесплатный триал
     *   - введён действующий промокод
     */
    fun shouldApplyLimits(trialDays: Int): Boolean {
        if (isPremiumUnlocked) return false
        if (isTrialActive(trialDays)) return false
        if (isPromoActive()) return false
        return true
    }

    companion object {
        /** Fallback-лимиты (используются если Remote Config недоступен) */
        const val MAX_PHOTOS = 100
        const val MAX_VIDEOS = 5

        private const val KEY_PHOTOS_COUNT = "photos_archived_count"
        private const val KEY_VIDEOS_COUNT = "videos_archived_count"
        private const val KEY_PREMIUM = "is_premium_unlocked"
        private const val KEY_TRUST_LEVEL = "trust_level"
        private const val KEY_PROMO_CODE = "applied_promo_code"
        private const val KEY_PROMO_UNTIL = "promo_until_timestamp"
        private const val KEY_LAST_SEEN_ANNOUNCEMENT = "last_seen_announcement_id"
    }
}

