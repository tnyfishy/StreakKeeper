import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.tnyfishy.sk"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.tnyfishy.sk"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        val storePath = keystoreProps.getProperty("storeFile")
            ?: System.getenv("KEYSTORE_FILE")
        val keyFile = storePath?.let { file(it) }?.takeIf { it.exists() }
        if (keyFile != null) {
            create("releaseKey") {
                storeFile = keyFile
                storePassword = keystoreProps.getProperty("storePassword")
                    ?: System.getenv("KEYSTORE_PASSWORD")
                    ?: ""
                keyAlias = keystoreProps.getProperty("keyAlias")
                    ?: System.getenv("KEY_ALIAS")
                    ?: ""
                keyPassword = keystoreProps.getProperty("keyPassword")
                    ?: System.getenv("KEY_PASSWORD")
                    ?: ""
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
            signingConfigs.findByName("releaseKey")?.let {
                signingConfig = it
            }
        }
        debug {
            signingConfigs.findByName("releaseKey")?.let {
                signingConfig = it
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Latest Stable Material 3 Expressive (1.5.0) + matching Compose 1.12.0
    implementation("androidx.compose.material3:material3:1.5.0")
    implementation("androidx.compose.ui:ui:1.12.0")
    implementation("androidx.compose.ui:ui-graphics:1.12.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.12.0")
    implementation("androidx.compose.foundation:foundation:1.12.0")
    implementation("androidx.compose.animation:animation:1.12.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.graphics:graphics-shapes:1.1.0")

    // Predictive Back / NavigationEvent (SDK 36+)
    implementation("androidx.navigationevent:navigationevent:1.0.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.0.0")

    // AndroidX Core, Activity, Lifecycle, SplashScreen
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.12.0")
}
