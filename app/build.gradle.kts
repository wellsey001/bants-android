plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Push notifications switch on once app/google-services.json (from Firebase) is in the repo.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.wellsey.bants"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wellsey.bants"
        minSdk = 26
        targetSdk = 34
        // GitHub Actions sets the build number, so every build installs as a newer version.
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()
        versionCode = 100 + build
        versionName = "1.0.$build"
    }

    // The release key is private: GitHub Actions writes it from the repo's secrets.
    // Builds without it (e.g. on your own computer) are left unsigned rather than signed with
    // a key anyone could copy.
    val keystore = file(System.getenv("BANTS_KEYSTORE_FILE") ?: "release.p12")
    val hasKey = keystore.exists() && System.getenv("BANTS_KEYSTORE_PASSWORD") != null
    signingConfigs {
        if (hasKey) {
            create("bants") {
                storeFile = keystore
                storeType = "PKCS12"
                storePassword = System.getenv("BANTS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BANTS_KEY_ALIAS") ?: "bants"
                keyPassword = System.getenv("BANTS_KEY_PASSWORD") ?: System.getenv("BANTS_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if (hasKey) signingConfig = signingConfigs.getByName("bants")
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")
    implementation("io.getstream:stream-webrtc-android:1.1.3")
    // Shrinking videos before sending (Standard / HD quality).
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging")
}
