plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ru.lexiloop.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.lexiloop.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.8.0"

        buildConfigField("String", "API_BASE_URL", "\"https://lexiloop.ru\"")
    }

    signingConfigs {
        // Self-signed key used for the sideloaded release APK. Overridable via
        // env vars so a private keystore can be swapped in without code changes.
        create("release") {
            storeFile = file(System.getenv("LEXILOOP_KEYSTORE") ?: "$rootDir/signing/lexiloop-release.p12")
            storeType = "PKCS12"
            storePassword = System.getenv("LEXILOOP_KEYSTORE_PASSWORD") ?: "lexiloop-release"
            keyAlias = System.getenv("LEXILOOP_KEY_ALIAS") ?: "lexiloop"
            keyPassword = System.getenv("LEXILOOP_KEY_PASSWORD") ?: "lexiloop-release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)

    // Installs the baseline profiles bundled with Compose/AndroidX so release
    // builds get ahead-of-time compiled hot paths even when sideloaded.
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
}
