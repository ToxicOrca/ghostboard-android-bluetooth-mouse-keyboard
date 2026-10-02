plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.remoteinput"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.remoteinput"
        minSdk = 28          // BluetoothHidDevice (HID Device Profile) 需要 API 28+
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // 轻量级拼音库（约 120KB，纯 Java 无额外资源），把汉字拆成拼音字母流，
    // 供「拼音」中继模式使用。包名为 com.github.promeg.pinyinhelper。
    //
    // 用 Maven Central 上的发布版而不是原作者的 JitPack 坐标
    // (com.github.promeg:tinypinyin:2.0.3)：JitPack 是按需构建的，
    // CI 首次拉取经常超时，而 Central 上的版本 API 完全一致、拉取稳定。
    implementation("io.github.biezhi:TinyPinyin:2.0.3.RELEASE")
}
