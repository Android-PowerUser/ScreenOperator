import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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

val missingReleaseSigningEnvText = missingReleaseSigningEnv.joinToString(separator = ", ")
val supportedAbis = listOf("arm64-v8a", "x86_64")

android {
    namespace = "com.screenoperator.humanoperator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.screenoperator.humanoperator"
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
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (missingReleaseSigningEnv.isEmpty()) signingConfigs.getByName("release") else null
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
        "Release signing env vars missing for module :humanoperator: ${missingReleaseSigningEnvText}. " +
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
    implementation("io.getstream:stream-webrtc-android:1.3.10")

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
