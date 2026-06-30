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
        versionCode = 58
        versionName = "2.0.0"
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
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
