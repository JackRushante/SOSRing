plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lorenzomarci.sosring"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lorenzomarci.sosring"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.2"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../sosring-release.jks")
            storePassword = "***REDACTED***"
            keyAlias = "sosring"
            keyPassword = "***REDACTED***"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "LOCATION_ENABLED", "false")
            buildConfigField("String", "UPDATE_URL", "\"\"")
        }
        create("internal") {
            dimension = "distribution"
            applicationIdSuffix = ".internal"
            buildConfigField("boolean", "LOCATION_ENABLED", "true")
            buildConfigField("String", "UPDATE_URL", "\"https://YOUR_NTFY_SERVER/update/\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Location sharing (internal flavor only)
    "internalImplementation"("com.google.android.gms:play-services-location:21.1.0")

    // HTTP client for ntfy
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
