import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Device (arm64) and the Apple-silicon simulator. iosX64 (Intel simulator)
    // is deliberately absent: Compose Multiplatform 1.11+ no longer publishes
    // artifacts for it.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget: KotlinNativeTarget ->
        iosTarget.binaries.framework {
            baseName = "JustACalculatorKit"
            // Static linkage keeps the Xcode side free of embed-and-sign plumbing.
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            // Coil 3 — multiplatform image loading (SVG icons from assets).
            implementation(libs.coil.compose)
            implementation(libs.coil.svg)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.benchmark.traceprocessor)

            // The key-examination overlay used to render here via
            // io.github.sceneview:sceneview (Filament). It now uses the shared
            // ModelViewerGl in commonMain, which is what iOS already used —
            // removing ~10.5 MB of native libraries and IBL environment maps.

            // CameraX.
            val cameraxVersion = "1.5.1"
            implementation("androidx.camera:camera-core:$cameraxVersion")
            implementation("androidx.camera:camera-camera2:$cameraxVersion")
            implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
            implementation("androidx.camera:camera-view:$cameraxVersion")

            // ML Kit on-device face detection — drives Building 7's vanity filter
            // (landmark anchoring for PNG overlays). On-device, offline, no API key.
            implementation("com.google.mlkit:face-detection:16.1.7")

            // AndroidSVG — rasterise the Building 7 vector filters straight to bitmaps.
            implementation("com.caverock:androidsvg-aar:1.4")
            // Process-wide lifecycle owner. The door room binds its cameras from
            // the GL thread, which has no composable lifecycle to hang them on.
            implementation("androidx.lifecycle:lifecycle-process:2.10.0")

            // osmdroid — OpenStreetMap tiles for Building 5. No API key, no Play Services.
            implementation("org.osmdroid:osmdroid-android:6.1.18")
        }

        iosMain.dependencies {
            // iOS actuals are written against the Apple SDKs Kotlin/Native exposes
            // directly (AVFoundation, Vision, MapKit, OpenGLES, …) — nothing extra.
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidUnitTest.dependencies {
            implementation(libs.junit)
        }
    }
}

// Compose Resources replaces Android's res/ for anything the shared UI needs
// (fonts today; drawables and raw audio as later phases move across).
compose.resources {
    publicResClass = true
    packageOfResClass = "com.fictioncutshort.justacalculator.resources"
    generateResClass = auto
}

android {
    namespace = "com.fictioncutshort.justacalculator"
    compileSdk = 36

    // KMP source-set layout: Android's non-Kotlin inputs live under androidMain
    // rather than the classic src/main.
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    // Game assets (models, textures, SVGs, audio) live in commonMain so the iOS
    // app bundle and the Android APK ship byte-identical copies from one source.
    // The Assets seam gives both platforms the same "models/x.obj" path scheme.
    sourceSets["main"].assets.srcDirs("src/commonMain/assets")

    // Story logic logs through android.util.Log, which is a stub that throws in
    // JVM unit tests. Returning defaults instead lets tests drive the real input
    // handler rather than only the pure helpers around it.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    defaultConfig {
        applicationId = "com.fictioncutshort.justacalculator"
        minSdk = 24
        targetSdk = 36
        versionCode = 16
        versionName = "1.16"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    dependencies {
        debugImplementation(compose.uiTooling)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
    }
}
