plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.openswipe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openswipe"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.4-fork"

        // English-only resources (drops AndroidX translations too, keeps the APK small).
        @Suppress("DEPRECATION")
        resourceConfigurations += "en"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Fixed signing key committed to the repo (personal fork, sideload only) so that every
    // CI build can be installed over the previous one with `pm install -r`.
    signingConfigs {
        create("fork") {
            storeFile = rootProject.file("keystore/openswipe-fork.jks")
            storePassword = "openswipe"
            keyAlias = "openswipe"
            keyPassword = "openswipe"
        }
    }

    flavorDimensions += "device"
    productFlavors {
        // Generic build: English UI.
        create("standard") {
            dimension = "device"
            buildConfigField("float", "DEFAULT_BOTTOM_HEIGHT_DP", "40f")
            buildConfigField("String", "DEFAULT_BLOCKED_ACTIVITIES", "\"\"")
        }
        // Numen Air U50 (Android 11, 240x320): English only, keyboard-safe defaults.
        // Defaults: left=Back, right=Back, bottom=Home (swipe-only, taps pass through),
        // bottom gesture paused while the soft keyboard is open.
        create("numen") {
            dimension = "device"
            versionNameSuffix = "-numen"
            // 40dp covered the bottom row of apps on the 240x320 screen; 20dp works.
            buildConfigField("float", "DEFAULT_BOTTOM_HEIGHT_DP", "20f")
            // Gestures paused in front of these. The g4home lock screen's "swipe up to
            // unlock" starts in our bottom Home zone, so unlocking jumped to the launcher.
            buildConfigField(
                "String", "DEFAULT_BLOCKED_ACTIVITIES",
                "\"com.glocalme.g4home/com.glocalme.basic.lock.LockScreenActivity," +
                    "com.glocalme.g4home/com.glocalme.basic.lock.FirstLockScreenActivity\"",
            )
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fork")
        }
        release {
            signingConfig = signingConfigs.getByName("fork")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.dynamicanimation.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
