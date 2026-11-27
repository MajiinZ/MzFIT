import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}
kotlin {
    androidTarget()
    jvm("desktop")
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    if (System.getProperty("os.name").startsWith("Mac")) {
        iosArm64()
        iosSimulatorArm64()
    }
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            api(libs.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
android {
    namespace = "com.mlpt.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}