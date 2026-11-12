import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing credentials live outside the project — in the user-global
// ~/.gradle/gradle.properties, never in the repo:
//   wise4chess.signing.storeFile / .storePassword / .keyAlias / .keyPassword
// When they are absent — other machines, CI — release builds fall back to debug
// signing so the build still works.
val signingStoreFile = (findProperty("wise4chess.signing.storeFile") as String?)
    ?.trim()
    ?.replaceFirst(Regex("^~"), System.getProperty("user.home"))
    ?.let(::File)
    ?.takeIf { it.isFile }
val signingStorePassword = (findProperty("wise4chess.signing.storePassword") as String?)?.takeIf { it.isNotBlank() }
val signingKeyAlias = (findProperty("wise4chess.signing.keyAlias") as String?)?.takeIf { it.isNotBlank() }
val signingKeyPassword = (findProperty("wise4chess.signing.keyPassword") as String?)?.takeIf { it.isNotBlank() }
// All four must be present — a half-configured keystore would fail mid-build.
val hasReleaseSigning =
    signingStoreFile != null && signingStorePassword != null && signingKeyAlias != null && signingKeyPassword != null

android {
    namespace = "io.github.wizard302.wise4chess"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.wizard302.wise4chess"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
    }

    androidResources {
        // The app's languages; also generates the per-app language list for system settings.
        localeFilters += listOf("en", "ru")
        generateLocaleConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:chess"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
