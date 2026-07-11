import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.lorenzomarci.sosring"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lorenzomarci.sosring"
        minSdk = 29
        targetSdk = 34
        versionCode = 42
        versionName = "2.18.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("internal") {
            dimension = "distribution"
            applicationIdSuffix = ".internal"
            buildConfigField("String", "UPDATE_URL", "\"${localProps.getProperty("UPDATE_URL", "")}\"")
            buildConfigField("String", "APP_SECRET", "\"${localProps.getProperty("APP_SECRET", "")}\"")
            buildConfigField("String", "NTFY_SERVER", "\"${localProps.getProperty("NTFY_SERVER", "")}\"")
            buildConfigField("String", "NTFY_AUTH_TOKEN", "\"${localProps.getProperty("NTFY_AUTH_TOKEN", "")}\"")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("String", "UPDATE_URL", "\"\"")
            buildConfigField("String", "APP_SECRET", "\"\"")
            buildConfigField("String", "NTFY_SERVER", "\"\"")
            buildConfigField("String", "NTFY_AUTH_TOKEN", "\"\"")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../sosring-release.jks")
            storePassword = localProps.getProperty("STORE_PASSWORD", "")
            keyAlias = "sosring"
            keyPassword = localProps.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            vcsInfo {
                include = false
            }
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

    "internalImplementation"("com.google.android.gms:play-services-location:21.3.0")

    // HTTP client for ntfy
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    implementation("org.maplibre.gl:android-sdk:13.3.0")

    // QR code generation for security passphrase display
    implementation("com.google.zxing:core:3.5.3")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    "fdroidImplementation"("org.unifiedpush.android:connector:3.3.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
