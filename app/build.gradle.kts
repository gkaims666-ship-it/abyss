import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Build version is stored in version.properties and bumped by the in-app
// self-update flow (v1.000000 -> v1.000001 ...). See VersionManager.
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}
val buildSerial = (versionProps.getProperty("serial") ?: "0").toInt()

android {
    namespace = "com.abyss.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.abyss.app"
        minSdk = 29
        targetSdk = 35
        versionCode = buildSerial + 1
        versionName = "1." + buildSerial.toString().padStart(6, '0')
        vectorDrawables { useSupportLibrary = true }
        // Ship only the 64-bit ARM libraries (Nothing Phone 3a and every modern phone):
        // the other three ABIs roughly doubled the APK for nothing.
        ndk { abiFilters += "arm64-v8a" }

        // Native local-model build is opt-in so the default APK always builds.
        // Enable with: ./gradlew assembleDebug -PwithLocalLlm
        if (project.hasProperty("withLocalLlm")) {
            externalNativeBuild {
                cmake { arguments += listOf("-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release") }
            }
        }
    }

    if (project.hasProperty("withLocalLlm")) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Persistence (chat history, memory/RAG store, settings)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Background self-improvement work
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Networking + JSON for provider APIs
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // On-device translation + language detection (no API key, works offline)
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")

    // Camera capture (video, photo, circles)
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Sky radar map (OpenStreetMap tiles; no API key)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Optional prebuilt native engines (e.g. a gomobile-built Xray .aar for the
    // VPN). Empty by default, so the standard APK is unaffected.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    debugImplementation("androidx.compose.ui:ui-tooling")
}
