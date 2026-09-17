import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val tdlibAbis = listOf("arm64-v8a", "armeabi-v7a")
val telegramApiId = providers.gradleProperty("telegramApiId")
    .map { it.toIntOrNull() ?: 0 }
    .orElse(0)
    .get()
val telegramApiHash = providers.gradleProperty("telegramApiHash")
    .orElse("")
    .get()
val telegramRealEnabled = telegramApiId > 0 && telegramApiHash.isNotBlank()

// Assinatura de release: valores em ~/.gradle/gradle.properties (fora do git). Se ausentes,
// o build de release sai sem assinatura (útil em CI/dev), mas não instala no aparelho.
val releaseStoreFile = providers.gradleProperty("releaseStoreFile").orNull
val releaseStorePassword = providers.gradleProperty("releaseStorePassword").orNull
val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias").orNull
val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword").orNull

abstract class VerifyTdlibNativeLibsTask : DefaultTask() {
    @get:Input
    abstract val realEnabled: Property<Boolean>

    @get:Input
    abstract val targetAbis: ListProperty<String>

    @get:InputDirectory
    @get:Optional
    abstract val jniRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        if (!realEnabled.get()) return

        val rootDir = jniRoot.get().asFile
        targetAbis.get().forEach { abi ->
            val abiDir = rootDir.resolve(abi)
            val hasNative = abiDir.resolve("libtdjni.so").exists() ||
                abiDir.resolve("libtdjsonjava.so").exists()
            if (!hasNative) {
                throw GradleException(
                    "TDLib nativa ausente para ABI '$abi'. " +
                        "Adicione libtdjni.so (ou libtdjsonjava.so) em ${abiDir.absolutePath}."
                )
            }
        }
    }
}

android {
    namespace = "com.ntv2.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ntv2.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.0"

        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId.toString())
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")
        buildConfigField(
            "boolean",
            "TELEGRAM_REAL_ENABLED",
            telegramRealEnabled.toString()
        )

        ndk {
            abiFilters += tdlibAbis
        }
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
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
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDirs("src/main/jniLibs")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.google.material)

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    // Decoder FFmpeg por software (ac3/eac3/dts/truehd) para aparelhos sem esses codecs.
    implementation(project(":ffmpeg-decoder"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.register<VerifyTdlibNativeLibsTask>("verifyTdlibNativeLibs") {
    realEnabled.set(telegramRealEnabled)
    targetAbis.set(tdlibAbis)
    jniRoot.set(layout.projectDirectory.dir("src/main/jniLibs"))
}

tasks.named("preBuild").configure {
    dependsOn("verifyTdlibNativeLibs")
}
