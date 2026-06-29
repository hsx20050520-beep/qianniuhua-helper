plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.qnh.helper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.qnh.helper"
        minSdk = 24
        targetSdk = 34
        versionCode = 56
        versionName = "1.5.9"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../qnh-keystore.jks")
            storePassword = "qnh2024helper"
            keyAlias = "qnh-key"
            keyPassword = "qnh2024helper"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
