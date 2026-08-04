plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

// Читаем local.properties через стандартный Gradle API (java.util не доступен напрямую в KTS DSL)
fun localProp(key: String): String? {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return null
    return file.readLines()
        .firstOrNull { it.startsWith("$key=") }
        ?.removePrefix("$key=")
        ?.trim()
}

android {
    namespace = "by.w6.my1drive"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "by.w6.my1drive2"
        minSdk = 28
        targetSdk = 36
        versionCode = 16
        versionName = "2.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProp("storeFile") ?: "my1drive-release-2.jks")
            storePassword = localProp("storePassword") ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = localProp("keyAlias") ?: "my1drive"
            keyPassword = localProp("keyPassword") ?: System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("ru.rustore.sdk:bom:2026.06.01"))
    implementation("ru.rustore.sdk:pay")
    implementation("ru.rustore.sdk:remoteconfig")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.documentfile)

    // Room DB
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coil Image Loading (with video support)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // Media3 (ExoPlayer)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // Icons Extended
    implementation(libs.androidx.compose.material.icons.extended)

    // SSH/SFTP Support
    implementation(libs.jsch)
    
    // AppMetrica Analytics
    implementation("io.appmetrica.analytics:analytics:7.3.0")

    // EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
