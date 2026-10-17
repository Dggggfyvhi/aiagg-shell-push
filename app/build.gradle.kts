plugins {
    id("com.android.application")
    kotlin("android")
}
android {
    namespace = "com.example.aiagg"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.aiagg"
        minSdk = 28
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.legacy:legacy-support-core-utils:1.0.0")
    implementation("com.google.android.material:material:1.11.0")
}
