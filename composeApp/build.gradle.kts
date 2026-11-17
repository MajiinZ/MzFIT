import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}
kotlin {
    androidTarget()
    jvm("desktop")
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    if (System.getProperty("os.name").startsWith("Mac")) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach {
            it.binaries.framework { baseName = "TrainingApp"; isStatic = true }
        }
    }
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(project(":shared"))
            implementation(project(":di"))
            implementation(project(":navigation"))
            implementation(project(":feature:training"))
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation("androidx.core:core-splashscreen:1.0.1")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
            implementation("androidx.camera:camera-camera2:1.4.2")
            implementation("androidx.camera:camera-lifecycle:1.4.2")
            implementation("androidx.camera:camera-view:1.4.2")
            implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
        }
        val androidInstrumentedTest by getting
        androidInstrumentedTest.dependencies {
            implementation("androidx.test:runner:1.6.2")
            implementation("androidx.test.ext:junit:1.2.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.coroutines.swing)
        }
    }
}
android {
    namespace = "com.mlpt.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mlpt.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
compose.desktop {
    application {
        mainClass = "com.mlpt.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "MlPtApp"
            packageVersion = "1.0.0"
        }
    }
}
