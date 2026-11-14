plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.doodlejoy.jarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.doodlejoy.jarvis"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // NOTE (Security rule from spec): NO API key is stored in the app.
        // Free AI chat goes through YOUR server endpoint below; the key stays on the server.
        buildConfigField("String", "AI_SERVER_URL", "\"https://your-server.example.com/chat\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
