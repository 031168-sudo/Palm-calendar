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
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Даты: на всех платформах одинаково (на iOS java.time нет)
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
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
        iosMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/iosStrings"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Тексты для iOS: те же strings.xml, что и на Android (русский и English), переложенные в таблицы Kotlin
val genIosStrings by tasks.registering {
    val resDir = rootProject.file("app/src/main/res")
    val outDir = layout.buildDirectory.dir("generated/iosStrings")
    inputs.dir(resDir)
    outputs.dir(outDir)
    doLast {
        fun unescape(t: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < t.length) {
                val c = t[i]
                if (c == '\\' && i + 1 < t.length) {
                    when (val n = t[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> { sb.append(t.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                        else -> sb.append(n)
                    }
                    i += 2
                } else { sb.append(c); i++ }
            }
            return sb.toString()
        }
        fun lit(t: String) = "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n") + "\""
        val sb = StringBuilder("package ru.palmdate.app.model\n\n// Создано автоматически из app/src/main/res/values*/strings.xml (задача genIosStrings)\n\n")
        val strOut = StringBuilder("internal val GENERATED_STRINGS: Map<String, Map<String, String>> = mapOf(\n")
        val plOut = StringBuilder("internal val GENERATED_PLURALS: Map<String, Map<String, Map<String, String>>> = mapOf(\n")
        for ((tag, dir) in listOf("en" to "values", "ru" to "values-ru")) {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(resDir, "$dir/strings.xml"))
            val strings = doc.getElementsByTagName("string")
            strOut.append("    ${lit(tag)} to mapOf(\n")
            for (i in 0 until strings.length) {
                val e = strings.item(i) as org.w3c.dom.Element
                strOut.append("        ${lit(e.getAttribute("name"))} to ${lit(unescape(e.textContent))},\n")
            }
            strOut.append("    ),\n")
            val pls = doc.getElementsByTagName("plurals")
            plOut.append("    ${lit(tag)} to mapOf(\n")
            for (i in 0 until pls.length) {
                val e = pls.item(i) as org.w3c.dom.Element
                plOut.append("        ${lit(e.getAttribute("name"))} to mapOf(")
                val items = e.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    val it2 = items.item(j) as org.w3c.dom.Element
                    plOut.append("${lit(it2.getAttribute("quantity"))} to ${lit(unescape(it2.textContent))}, ")
                }
                plOut.append("),\n")
            }
            plOut.append("    ),\n")
        }
        strOut.append(")\n\n"); plOut.append(")\n")
        val f = File(outDir.get().asFile, "ru/palmdate/app/model/GeneratedStrings.kt")
        f.parentFile.mkdirs()
        f.writeText(sb.toString() + strOut + plOut)
    }
}
tasks.configureEach { if (name.startsWith("compileKotlin") || name.contains("SourcesJar")) dependsOn(genIosStrings) }

android {
    namespace = "ru.palmdate.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
