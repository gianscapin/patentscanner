import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.gradle.kotlin.dsl.register
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.soflex.lectorpatente"
    compileSdk {
        version = release(36)
    }

    val buildVersionCode = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyMMddHH"))
        .toInt()

    val APP_VERSION_NAME: String by project


    signingConfigs {
        create("release") {
            storeFile = file("keystore2.jks")
            keyAlias = "upload"
            storePassword = "Cerrito2323"
            keyPassword = "Cerrito2323"
        }
    }

    applicationVariants.all {
        val flavorCustomValues = mapOf(
            "com.soflex.lectorpatente" to "10196",
        )

        val variant = this
        variant.outputs
            .map { it as BaseVariantOutputImpl }
            .forEach { output ->
                val format = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault())
                val date = format.format(Date())
                val flavorName = variant.flavorName ?: "default"
                val buildType = variant.buildType.name
                val versionName = variant.versionName
                val versionCode = variant.versionCode
                val arch = output.getFilter(com.android.build.OutputFile.ABI) ?: "universal"

                val uploadAppId = flavorCustomValues[applicationId]

                val versionInfo = "$versionName($versionCode)"
                val archLabel = if (arch.isNotEmpty()) "-$arch" else ""
                val baseFileName = "$date-$flavorName-$buildType-$versionInfo$archLabel"

                output.outputFileName = if (uploadAppId != null) {
                    "$baseFileName-[${versionCode}-$uploadAppId-$versionName].apk"
                } else {
                    "$baseFileName.apk"
                }
            }
    }

    defaultConfig {
        applicationId = "com.soflex.lectorpatente"
        minSdk = 24
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = APP_VERSION_NAME

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Configuración NDK para PaddleOCR
        // Solo arm64-v8a (dispositivos modernos 2016+)
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

        }
    }

    tasks.register("buildAllFlavours") {
        group = "build"
        description = "Compila todas las variantes necesarias"

        dependsOn(
            "assembleRelease",
        )
    }

    val uploadToken: String by project
    tasks.register<UploadApksTask>("uploadApks") {
        token.set(uploadToken)
        apkDirectory.set(layout.buildDirectory.dir("outputs/apk"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    // Configuración NDK para PaddleOCR
    ndkVersion = "25.1.8937393"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.6")

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit Text Recognition
    implementation(libs.mlkit.text.recognition)

    // Tesseract OCR - OpenMP variant para mejor rendimiento
    implementation("cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

abstract class UploadApksTask : DefaultTask() {

    @get:Input
    abstract val token: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @TaskAction
    fun upload() {
        val outputDir = apkDirectory.get().asFile
        println("🔍 Buscando APKs en ${outputDir}...")

        val apks = outputDir.walkTopDown().filter { it.isFile && it.name.endsWith(".apk") }

        apks.forEach { apk ->
            val regex = Regex("""\[(\d+)-(\d+)-([^\]]+)]\.apk$""")
            val match = regex.find(apk.name)
            if (match != null) {
                val version = match.groupValues[1]
                val aplicacionid = match.groupValues[2]
                val versionName = match.groupValues[3]

                println("🚀 Subiendo ${apk.name} (AppID: $aplicacionid, Version: $version, VersionName: $versionName)")

                val process = ProcessBuilder(
                    "curl", "--verbose", "--silent", "--show-error", "--fail",
                    "--request", "POST",
                    "--url", "https://stgptt.soflex.com.ar/mdm/admin/application-version",
                    "--header", "authorization: Bearer ${token.get()}",
                    "--form", "aplicacionid=$aplicacionid",
                    "--form", "version=$version",
                    "--form", "nombre=$versionName",
                    "--form", "archivo=@${apk.absolutePath}"
                ).start()

                val errors = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    println("✅ Subido con éxito: ${apk.name}")
                } else {
                    println("❌ Error al subir ${apk.name}:\n$errors")
                }
            } else {
                println("⚠️ Archivo ignorado (no coincide con patrón): ${apk.name}")
            }
        }
    }
}