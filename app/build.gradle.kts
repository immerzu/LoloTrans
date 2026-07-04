import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val telegramApiId: String = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) { "0" } else {
        Properties().apply { f.inputStream().use { load(it) } }
            .getProperty("TELEGRAM_API_ID", "0")
    }
}

val telegramApiHash: String = run {
    val f = rootProject.file("local.properties")
    if (!f.exists()) { "" } else {
        Properties().apply { f.inputStream().use { load(it) } }
            .getProperty("TELEGRAM_API_HASH", "")
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.lolo.lolotrans"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.lolo.lolotrans"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "2.2"
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "ML_KIT_AVAILABLE", "true")
            buildConfigField("boolean", "EXTERNAL_PROVIDER_AVAILABLE", "false")
            buildConfigField("String", "DEFAULT_PROVIDER", "\"ML_KIT\"")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "ML_KIT_AVAILABLE", "false")
            buildConfigField("boolean", "EXTERNAL_PROVIDER_AVAILABLE", "false")
            buildConfigField("String", "DEFAULT_PROVIDER", "\"LIBRE_TRANSLATE\"")
        }
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "ML_KIT_AVAILABLE", "false")
            buildConfigField("boolean", "EXTERNAL_PROVIDER_AVAILABLE", "true")
            buildConfigField("String", "DEFAULT_PROVIDER", "\"LIBRE_TRANSLATE\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Telegram TDLib JSON wrapper with Android native libraries.
    "fullImplementation"("io.github.xephosbot:tdlib-kmp:1.8.62")

    // ML Kit Translation (only in full flavor)
    "fullImplementation"("com.google.mlkit:translate:17.0.3")
    "fullImplementation"("com.google.mlkit:language-id:17.0.6")
    "fullImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
