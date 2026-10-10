/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.writer_hub.novelserise"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.app.novelfm"
        minSdk = 26
        targetSdk = 36
        versionCode = 62
        versionName = "5.6.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Android Studio uses default debug signing configuration automatically
        }
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
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// After every assembleDebug/assembleRelease, keep a shareable copy named with the build time,
// e.g. apk_builds/NovelFM_debug_v5.6.2_2026-10-01_14-38.apk, so old builds are never overwritten.
listOf("debug", "release").forEach { buildType ->
    val cap = buildType.replaceFirstChar { it.uppercase() }
    val copyTask = tasks.register("copyTimestamped${cap}Apk") {
        // Plain values only, so the task stays configuration-cache safe.
        val outDir = rootProject.layout.projectDirectory.dir("apk_builds").asFile
        val apkDir = layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile
        val appVersionName = android.defaultConfig.versionName
        doLast {
            val stamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
            outDir.mkdirs()
            apkDir.listFiles { f -> f.extension == "apk" }?.forEach { apk ->
                val target = File(outDir, "NovelFM_${buildType}_v${appVersionName}_$stamp.apk")
                apk.copyTo(target, overwrite = true)
                println("Shareable APK: ${target.absolutePath}")
            }
        }
    }
    tasks.matching { it.name == "assemble$cap" }.configureEach { finalizedBy(copyTask) }
}

dependencies {
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.ads)
    coreLibraryDesugaring(libs.desugarJdkLibs)
    
    implementation(project(":storymania"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.swiperefreshlayout)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.guava)

    // FFmpeg
    implementation("io.github.maxrave-dev:ffmpeg-kit-audio:6.0.1")

    // Razorpay
    implementation(libs.checkout)

    // Firebase & Google Auth
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.play.services.auth)

    // Lottie
    implementation(libs.lottie)
    
    // Shimmer
    implementation(libs.facebook.shimmer)

    // Image Cropper
    implementation(libs.android.image.cropper)
}
