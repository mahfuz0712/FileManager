plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Bump ONLY this when you release (tag the GitHub release with the same number, e.g. v1.0.1)
val appVersionName = "1.0.0"

android {
    namespace = "com.mux.android.files"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mux.android.files"
        minSdk = 26
        targetSdk = 34
        versionName = appVersionName
        versionCode = appVersionName.split(".").let { it[0].toInt() * 10000 + it[1].toInt() * 100 + it[2].toInt() }
    }

    // Same debug key on every CI build, so a new APK installs over the old one as an update.
    // app/debug.keystore is used when present, so updates install over old builds.
    val ks = file("debug.keystore")
    if (ks.exists()) {
        signingConfigs {
            getByName("debug") {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi", "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi", "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")
}
