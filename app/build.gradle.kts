plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.audioma"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.audioma"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Clé de release lue depuis ~/.gradle/gradle.properties (hors dépôt) :
    // AUDIOMA_STORE_FILE, AUDIOMA_STORE_PASSWORD, AUDIOMA_KEY_ALIAS, AUDIOMA_KEY_PASSWORD
    val releaseStoreFile = providers.gradleProperty("AUDIOMA_STORE_FILE").orNull
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("AUDIOMA_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("AUDIOMA_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("AUDIOMA_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("AUDIOMA_STORE_FILE absent : APK release signé avec la clé de debug.")
                signingConfigs.getByName("debug")
            }
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
}
