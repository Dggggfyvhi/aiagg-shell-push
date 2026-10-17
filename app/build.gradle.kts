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
    // AppCompat：MainActivity 继承 AppCompatActivity，需要
    implementation("androidx.appcompat:appcompat:1.6.1")
    // LocalBroadcastManager：服务与 Activity 间本地通信
    implementation("androidx.legacy:legacy-support-core-utils:1.0.0")
    // Material 组件（可选，用于更美观的 UI，不引入则用系统控件）
    implementation("com.google.android.material:material:1.11.0")
}
