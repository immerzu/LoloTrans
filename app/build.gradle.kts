plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.lolo.lolotrans"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.lolo.lolotrans"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "2.1"
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

    kotlinOptions {
        jvmTarget = "17"
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

    // ML Kit Translation (only in full flavor)
    "fullImplementation"("com.google.mlkit:translate:17.0.3")
    "fullImplementation"("com.google.mlkit:language-id:17.0.6")
    "fullImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
