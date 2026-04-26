plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

val releaseSigningEnv = mapOf(
    "ANDROID_KEYSTORE_PATH" to System.getenv("ANDROID_KEYSTORE_PATH"),
    "ANDROID_KEY_ALIAS" to System.getenv("ANDROID_KEY_ALIAS"),
    "ANDROID_KEYSTORE_PASSWORD" to System.getenv("ANDROID_KEYSTORE_PASSWORD"),
    "ANDROID_KEY_PASSWORD" to System.getenv("ANDROID_KEY_PASSWORD"),
)

val missingReleaseSigningEnv = releaseSigningEnv
    .filterValues { it.isNullOrBlank() }
    .keys

val isReleaseTaskRequested = gradle.startParameter.taskNames.any { task ->
    task.contains("release", ignoreCase = true)
}

android {
    namespace = "com.screenoperator.humanoperator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.screenoperator.humanoperator"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (missingReleaseSigningEnv.isEmpty()) {
                storeFile = file(releaseSigningEnv.getValue("ANDROID_KEYSTORE_PATH")!!)
                storePassword = releaseSigningEnv.getValue("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningEnv.getValue("ANDROID_KEY_ALIAS")
                keyPassword = releaseSigningEnv.getValue("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (missingReleaseSigningEnv.isEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

if (isReleaseTaskRequested && missingReleaseSigningEnv.isNotEmpty()) {
    error(
        "Release signing env vars missing for module :humanoperator: ${missingReleaseSigningEnv.joinToString(", ")}. " +
            "Set ANDROID_KEYSTORE_PATH, ANDROID_KEY_ALIAS, ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_PASSWORD."
    )
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // WebRTC
    implementation("io.getstream:stream-webrtc-android:1.1.1")

    // WebSocket for signaling
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-database")
}
