# =====================================================
# My1drive ProGuard / R8 Rules
# =====================================================

# Сохраняем номера строк для Crashlytics/AppMetrica
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Kotlin ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ---- Kotlinx Coroutines ----
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---- RuStore SDK (Pay + RemoteConfig) ----
-keep class ru.rustore.sdk.** { *; }
-dontwarn ru.rustore.sdk.**

# ---- AppMetrica ----
-keep class io.appmetrica.analytics.** { *; }
-dontwarn io.appmetrica.analytics.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.**

# ---- Coil ----
-dontwarn coil.**

# ---- JSch (SSH/SFTP) ----
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# ---- Media3 / ExoPlayer ----
-dontwarn androidx.media3.**

# ---- EncryptedSharedPreferences ----
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ---- Jetpack Compose ----
-dontwarn androidx.compose.**

# ---- DocumentFile ----
-keep class androidx.documentfile.provider.** { *; }

# ---- JSON (org.json встроен в Android — не трогаем) ----
-keep class org.json.** { *; }

# ---- Наши data/model классы (Room, JSON) ----
-keep class by.w6.my1drive.domain.model.** { *; }
-keep class by.w6.my1drive.data.local.**Entity { *; }
-keep class by.w6.my1drive.billing.PromoCodeEntry { *; }

# ---- Убираем лишние предупреждения ----
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**