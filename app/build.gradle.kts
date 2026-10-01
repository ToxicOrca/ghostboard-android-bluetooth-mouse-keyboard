plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // namespace 保持与原仓库一致，避免搬迁源码目录；对外包名由 applicationId 控制
    namespace = "com.example.remoteinput"
    compileSdk = 34

    defaultConfig {
        // 独立开发包名，可与原版 GhostBoard 共存，互不覆盖
        applicationId = "com.dev.hidrelay.keyboard"
        minSdk = 28          // BluetoothHidDevice (HID Device Profile) 需要 API 28+
        targetSdk = 34
        versionCode = 2
        versionName = "1.1-relay"

        resourceConfigurations += listOf("zh", "en")
    }

    /**
     * 签名配置：
     *  - v1 (JAR signing)  -> Android 6.0 及以下的兼容签名
     *  - v2 (APK Signature Scheme v2) -> Android 7.0+ 整包校验，装机拦截率最低
     *  - v3 (APK Signature Scheme v3) -> Android 9.0+ 密钥轮换支持
     * 三者同时开启是当前国内 ROM（ColorOS / HyperOS / OriginOS）安装器最认可的形态。
     *
     * 密钥库路径可用环境变量覆盖：
     *   GHOSTBOARD_KEYSTORE / GHOSTBOARD_STORE_PASSWORD / GHOSTBOARD_KEY_ALIAS / GHOSTBOARD_KEY_PASSWORD
     */
    val keystoreFile = rootProject.file("keystore/ghostboard-dev.jks")
    val keystoreExists = keystoreFile.exists()

    signingConfigs {
        create("ghostboard") {
            if (keystoreExists) {
                storeFile = keystoreFile
                storePassword = System.getenv("GHOSTBOARD_STORE_PASSWORD") ?: "ghostboard"
                keyAlias = System.getenv("GHOSTBOARD_KEY_ALIAS") ?: "ghostboard"
                keyPassword = System.getenv("GHOSTBOARD_KEY_PASSWORD") ?: "ghostboard"
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            // Debug 包也使用同一套签名，避免国内 ROM 对 debug 证书的额外告警
            signingConfig = if (keystoreExists) {
                signingConfigs.getByName("ghostboard")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystoreExists) {
                signingConfigs.getByName("ghostboard")
            } else {
                signingConfigs.getByName("debug")
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }

    buildFeatures {
        viewBinding = false
    }
}

dependencies {
    // ⚠️ compileSdk = 34 时必须使用 34 兼容版本：
    //    core-ktx 1.15+ / appcompat 1.7.1+ 均以 compileSdk 35 编译，会导致 AAR metadata 校验失败
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 轻量级拼音库（约 120KB，纯 Java 无额外资源），把汉字拆成拼音字母流。
    // 注意：TinyPinyin 原始坐标 com.github.promeg:tinypinyin 托管在 JitPack，
    //      国内网络经常拉不到；这里改用 Maven Central 上的等价发布版本，
    //      包名仍是 com.github.promeg.pinyinhelper，API 完全一致。
    implementation("io.github.biezhi:TinyPinyin:2.0.3.RELEASE")
}
