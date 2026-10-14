import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Общий код для Android и iOS: чистая логика без Android и без строк интерфейса.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // iOS-цели собираются только на macOS; на Linux (наш CI для Android) Gradle их пропускает
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Даты: на всех платформах одинаково (на iOS java.time нет)
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            // Экраны на Compose Multiplatform: общие для Android и iOS
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)
        }
        androidMain.dependencies {
            api("androidx.activity:activity-compose:1.9.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "ru.palmdate.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
