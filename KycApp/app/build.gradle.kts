plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

// Change for your LAN PC IP (phone must reach backend :8090). Emulator: http://10.0.2.2:8090
val dfsApiBase = "http://192.168.1.24:8090"

android {
    namespace = "com.example.kycapp"
    // LiteRT's transitive deps (androidx.core 1.15+, lifecycle 2.10+, compose-ui
    // 1.9+) require compiling against API 35+. targetSdk/minSdk are unrelated
    // (and left as-is) -- compileSdk only controls which APIs are visible at
    // build time, not runtime opt-in behavior.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kycapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "DFS_API_BASE", "\"$dfsApiBase\"")

        // Size: ship only 64-bit ARM for phone testing/release.
        // (x86_64 emulator / 32-bit ARM omitted from APK to cut native .so bulk.)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            // Minify OFF: R8 was stripping Tesseract/ONNX/LiteRT/MediaPipe/OpenCV
            // JNI entry points and crashing OCR + face match on device.
            // Size win kept via arm64-only abiFilters above.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug keystore so the APK sideloads for local testing without a release key.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs.pickFirsts.add("**/libc++_shared.so")
    }

    androidResources {
        // TFLite's Interpreter memory-maps its model asset, which requires the
        // entry be stored uncompressed in the APK; keep the ONNX model
        // uncompressed too for consistency/faster first load.
        noCompress += listOf("tflite", "onnx")
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // FlashOn/Off + Cameraswitch live in the extended set (core lacks them).
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // CameraX
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Fingerprint / Biometrics (for enroll/match hardware prompt, optional)
    implementation("androidx.biometric:biometric:1.1.0")

    // OpenCV (fingerprint matching: ORB/AKAZE feature extraction + matching)
    // 4.14.0+ ships 16KB-page-aligned native libs (4.10.0 did not).
    implementation("org.opencv:opencv:4.14.0")

    // MediaPipe Tasks Vision (FaceLandmarker -- blink liveness + face alignment landmarks)
    implementation("com.google.mediapipe:tasks-vision:0.10.29")

    // LiteRT (on-device ID-card classifier, converted from latest_model.h5).
    // LiteRT is Google's rebrand/successor to org.tensorflow:tensorflow-lite,
    // keeping the identical org.tensorflow.lite.Interpreter API -- switched
    // to it because tensorflow-lite 2.17.0 (the last release under the old
    // artifact) never shipped a 16KB-page-aligned libtensorflowlite_jni.so
    // for x86_64, while LiteRT's native libs are aligned on every ABI.
    implementation("com.google.ai.edge.litert:litert:2.2.0")

    // ONNX Runtime Mobile (on-device face embedding, InsightFace w600k_r50 FP16)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")

    // Tesseract OCR (on-device CNIC text extraction)
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    // Room + SQLCipher (encrypted on-device storage for enrolled biometrics)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("net.zetetic:sqlcipher-android:4.13.0@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Coroutines (Room suspend DAOs + background OpenCV/MediaPipe work)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // EXIF-aware image loading -- BitmapFactory.decodeFile() ignores the EXIF
    // orientation tag CameraX writes into captured JPEGs on real devices.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
