plugins {
    id("com.android.library")
}

// Módulo que empacota o FfmpegAudioRenderer do Media3 (fontes oficiais da tag 1.5.1)
// junto com as libs estáticas do FFmpeg 6.0 compiladas para as 4 ABIs (ac3/eac3/dts/
// truehd). Permite decodificar por software áudios que o hardware do aparelho não suporta.
android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    ndkVersion = "26.1.10909125"

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.exoplayer)
    implementation("androidx.annotation:annotation:1.8.2")
    compileOnly("org.checkerframework:checker-qual:3.42.0")
}
