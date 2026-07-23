package by.w6.my1drive.data.local

import android.content.Context
import android.content.SharedPreferences

class LimitRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("limits_prefs", Context.MODE_PRIVATE)

    var photosArchivedCount: Int
        get() = prefs.getInt(KEY_PHOTOS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_PHOTOS_COUNT, value).apply()

    var videosArchivedCount: Int
        get() = prefs.getInt(KEY_VIDEOS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_VIDEOS_COUNT, value).apply()

    var isPremiumUnlocked: Boolean
        get() = prefs.getBoolean(KEY_PREMIUM, false)
        set(value) = prefs.edit().putBoolean(KEY_PREMIUM, value).apply()

    var trustLevel: Int
        get() = prefs.getInt(KEY_TRUST_LEVEL, 0)
        set(value) = prefs.edit().putInt(KEY_TRUST_LEVEL, value).apply()

    companion object {
        const val MAX_PHOTOS = 100
        const val MAX_VIDEOS = 5
        
        private const val KEY_PHOTOS_COUNT = "photos_archived_count"
        private const val KEY_VIDEOS_COUNT = "videos_archived_count"
        private const val KEY_PREMIUM = "is_premium_unlocked"
        private const val KEY_TRUST_LEVEL = "trust_level"
    }
}
