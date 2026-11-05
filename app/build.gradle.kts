import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.busic.app"
    compileSdk = 36
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.busic.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        resourceConfigurations += "zh"
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "DebugProbesKt.bin",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
}

tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
    doLast {
        val variant = if (name.contains("Release")) "release" else "debug"
        val apkDir = layout.buildDirectory.dir("outputs/apk/$variant").get().asFile
        val apk = apkDir.listFiles()
            ?.filter { it.extension == "apk" }
            ?.maxByOrNull { it.lastModified() }
            ?: return@doLast
        stripZipPadding(apk, android.sdkDirectory)
        val version = android.defaultConfig.versionName ?: "0.1.0"
        val named = File(apkDir, "bilibili-music-$version.apk")
        if (apk.absolutePath != named.absolutePath) {
            apk.copyTo(named, overwrite = true)
            apk.delete()
        }
        apkDir.listFiles()?.filter { it.extension == "apk" && it.name != named.name }?.forEach { it.delete() }
    }
}

fun stripZipPadding(apk: File, sdkDir: File) {
    val raw = File(apk.parentFile, "${apk.name}.raw")
    val aligned = File(apk.parentFile, "${apk.name}.aligned")
    ZipFile(apk).use { zip ->
        ZipOutputStream(raw.outputStream()).use { out ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryName = entry.name
                val ext = entryName.substringAfterLast('.', "").uppercase()
                if (entryName.startsWith("META-INF/") && ext in setOf("SF", "RSA", "DSA", "MF")) continue
                val next = ZipEntry(entryName)
                next.method = entry.method
                next.time = entry.time
                if (entry.method == ZipEntry.STORED) {
                    next.size = entry.size
                    next.compressedSize = entry.size
                    next.crc = entry.crc
                }
                out.putNextEntry(next)
                zip.getInputStream(entry).use { input -> input.copyTo(out) }
                out.closeEntry()
            }
        }
    }
    val tools = File(sdkDir, "build-tools/34.0.0")
    val windows = System.getProperty("os.name").startsWith("Windows")
    val zipalign = File(tools, if (windows) "zipalign.exe" else "zipalign")
    val apksigner = File(tools, if (windows) "apksigner.bat" else "apksigner")
    val keystore = File(System.getProperty("user.home"), ".android/debug.keystore")
    project.exec { commandLine(zipalign.absolutePath, "-p", "-f", "4", raw.absolutePath, aligned.absolutePath) }
    project.exec {
        commandLine(
            apksigner.absolutePath, "sign",
            "--ks", keystore.absolutePath,
            "--ks-pass", "pass:android",
            "--key-pass", "pass:android",
            "--out", apk.absolutePath,
            aligned.absolutePath,
        )
    }
    raw.delete()
    aligned.delete()
    File(apk.parentFile, "${apk.name}.idsig").delete()
}
