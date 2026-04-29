import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
}

// Optional override for local build directory (e.g. external storage issues on specific setups).
// If not provided, Gradle default build directory is used.
System.getenv("SCREENOPERATOR_BUILD_DIR")?.takeIf { it.isNotBlank() }?.let { customBuildDir ->
    layout.buildDirectory = file(customBuildDir)
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

val missingReleaseSigningEnvText = missingReleaseSigningEnv.joinToString(separator = ", ")
val supportedAbis = listOf("arm64-v8a", "x86_64")

android {
    namespace = "com.google.ai.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.android_poweruser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += supportedAbis
        }

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
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") {
            isDebuggable = false
            signingConfig = if (missingReleaseSigningEnv.isEmpty()) signingConfigs.getByName("release") else null
        }
        create("samples") {
            initWith(getByName("debug"))
            isDebuggable = false
        }
    }

    sourceSets.getByName("samples") {
        java.setSrcDirs(listOf("src/main/java", "src/main/kotlin", "../../samples/src/main/java"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

fun parseLoadAlignments(readelfOutput: String): List<Long> {
    val lines = readelfOutput.lineSequence().toList()
    val alignments = mutableListOf<Long>()
    for (index in 0 until lines.lastIndex) {
        if (!lines[index].trimStart().startsWith("LOAD")) continue
        val alignToken = lines[index + 1].trim().split(Regex("\\s+")).lastOrNull() ?: continue
        val alignValue = alignToken.removePrefix("0x").toLongOrNull(16) ?: continue
        alignments += alignValue
    }
    return alignments
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name
        val variantNameCap = variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val mergeNativeTaskName = "merge${variantNameCap}NativeLibs"
        val verifyTaskName = "verify${variantNameCap}Native16KbAlignment"

        val verifyTask = tasks.register(verifyTaskName) {
            group = "verification"
            description = "Verifies that all merged native libs for $variantName use at least 16KB PT_LOAD alignment."
            dependsOn(mergeNativeTaskName)

            doLast {
                val nativeOutDir = layout.buildDirectory
                    .dir("intermediates/merged_native_libs/$variantName/$mergeNativeTaskName/out/lib")
                    .get()
                    .asFile

                if (!nativeOutDir.exists()) {
                    throw GradleException("Native lib output directory not found: ${nativeOutDir.absolutePath}")
                }

                val soFiles = nativeOutDir.walkTopDown().filter { it.isFile && it.extension == "so" }.toList()
                val filteredSoFiles = soFiles.filter { soFile ->
                    val abiDir = soFile.parentFile?.name
                    abiDir in supportedAbis
                }
                if (filteredSoFiles.isEmpty()) {
                    logger.lifecycle("No native .so files found under ${nativeOutDir.absolutePath} for variant $variantName.")
                    return@doLast
                }

                val invalidLibraries = mutableListOf<String>()
                filteredSoFiles.forEach { soFile ->
                    val stdout = ByteArrayOutputStream()
                    val execResult = exec {
                        commandLine("readelf", "-l", soFile.absolutePath)
                        standardOutput = stdout
                        isIgnoreExitValue = false
                    }
                    if (execResult.exitValue != 0) {
                        throw GradleException("readelf failed for ${soFile.absolutePath}")
                    }

                    val alignments = parseLoadAlignments(stdout.toString())
                    if (alignments.isEmpty() || alignments.any { it < 0x4000L }) {
                        val relativePath = soFile.relativeTo(nativeOutDir).path
                        val shownAlignments = if (alignments.isEmpty()) "none" else alignments.joinToString(", ") { "0x${it.toString(16)}" }
                        invalidLibraries += "$relativePath (PT_LOAD alignments: $shownAlignments)"
                    }
                }

                if (invalidLibraries.isNotEmpty()) {
                    throw GradleException(
                        "Found native libraries without required 16KB alignment in variant '$variantName':\n" +
                            invalidLibraries.joinToString("\n")
                    )
                }
            }
        }

        tasks.configureEach {
            if (name == "assemble$variantNameCap") {
                dependsOn(verifyTask)
            }
        }
    }
}

if (isReleaseTaskRequested && missingReleaseSigningEnv.isNotEmpty()) {
    error(
        "Release signing env vars missing for module :app: ${missingReleaseSigningEnvText}. " +
            "Set ANDROID_KEYSTORE_PATH, ANDROID_KEY_ALIAS, ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_PASSWORD."
    )
}

dependencies {
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
        implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.20")
    }

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Required for Android Accessibility Service
    implementation("androidx.core:core:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Required for one-shot operations (to use `ListenableFuture` from Guava Android)
    implementation("com.google.guava:guava:31.0.1-android")

    implementation("com.google.code.gson:gson:2.10.1")

    // Required for streaming operations (to use `Publisher` from Reactive Streams)
    implementation("org.reactivestreams:reactive-streams:1.0.4")

    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("io.coil-kt:coil-compose:2.5.0")

    // Google Play Billing Library
    implementation("com.android.billingclient:billing-ktx:7.1.1") // Latest version as per documentation

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // MediaPipe GenAI for offline inference (LLM)
    implementation("com.google.mediapipe:tasks-genai:0.10.32")
    // LiteRT-LM for newer offline .litertlm models (e.g. Gemma 4 E4B it)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")

    // Camera Core to potentially fix missing JNI lib issue
    implementation("androidx.camera:camera-core:1.4.2")

    // WebRTC
    implementation("io.getstream:stream-webrtc-android:1.3.10")

    // WebSocket for signaling
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    implementation("com.google.firebase:firebase-database")
}
