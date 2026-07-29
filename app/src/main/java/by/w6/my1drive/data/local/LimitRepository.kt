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

    companion object {
        const val MAX_PHOTOS = 100
        const val MAX_VIDEOS = 5
        
        private const val KEY_PHOTOS_COUNT = "photos_archived_count"
        private const val KEY_VIDEOS_COUNT = "videos_archived_count"
        private const val KEY_PREMIUM = "is_premium_unlocked"
        private const val KEY_TRUST_LEVEL = "trust_level"
    }
}
