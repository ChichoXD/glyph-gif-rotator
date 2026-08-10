plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.glyphrotator.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.glyphrotator.app"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        // Para poder enseñar la versión de la app en el reporte de fallos: sin ella, la mitad
        // de los reportes llegan sin decir de qué versión hablan y no se pueden ni empezar.
        buildConfig = true
    }
}

dependencies {
    // Nothing Glyph Matrix SDK — download glyph-matrix-sdk-2.0.aar yourself and place it
    // in app/libs/. See app/libs/README.md for instructions (not redistributed here, see
    // the SDK's EULA).
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Recorrer la carpeta de sprites que elige el usuario en el selector (SAF).
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Decodes GIFs frame-by-frame (bitmap + duration per frame) for the Glyph Matrix,
    // and also renders animated previews in the GIF list.
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.29")

    // Las mecánicas de Pokémon (niveles, evoluciones) son lógica pura sin dependencias de
    // Android, así que se pueden verificar con tests locales sin dispositivo.
    testImplementation("junit:junit:4.13.2")
}
