plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vantage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vantage"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }

        buildConfigField(
            "String", "UNSPLASH_ACCESS_KEY",
            "\"${project.findProperty("UNSPLASH_ACCESS_KEY") ?: ""}\""
        )
        buildConfigField(
            "String", "ELEVENLABS_API_KEY",
            "\"${project.findProperty("ELEVENLABS_API_KEY") ?: ""}\""
        )
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // Required so QNN .so files are extracted to disk rather than loaded from the APK zip.
        // Without this, dlopen fails because the QNN runtime can't mmap compressed entries.
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.navigation.compose)

    // CameraX
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Image loading (Unsplash thumbnails)
    implementation(libs.coil.compose)

    // Networking (Unsplash API)
    implementation(libs.okhttp)

    // LiteRT-LM (Gemma on-device)
    implementation(libs.litertlm)

    // Coroutines
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
