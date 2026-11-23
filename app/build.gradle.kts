plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Public trial uses a dedicated certificate stored outside this repository.
// Missing signing environment produces an unsigned APK, never a debug-signed release.
val appVersionName = "0.1.13"
val publicStorePath = System.getenv("CARDECK_RELEASE_STORE")
val publicStorePassword = System.getenv("CARDECK_RELEASE_PASSWORD")
val hasReleaseKeystore = publicStorePath != null && publicStorePassword != null
android {
    namespace = "io.github.shaonialife.cardeck"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.shaonialife.cardeck.bmwprobe"
        minSdk = 31
        targetSdk = 34
        versionCode = 165
        versionName = "$appVersionName-v47-public"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(requireNotNull(publicStorePath))
                storePassword = publicStorePassword
                keyAlias = "cardeck-bmw"
                keyPassword = publicStorePassword
            }
        }
    }

    buildTypes {
        release {
            // **不做代码混淆**：本项目依赖反射（SystemProperties、Shizuku API）与
            // 自定义 AIDL，R8 需要额外 keep 规则；首版先保证可运行，混淆留待后续。
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else null
        }
        debug {
            // The iDrive 6 field test can be installed alongside the center trial.
            applicationIdSuffix = ".id6test"
            versionNameSuffix = "-oneplus-id6-video-test"
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
        aidl = true
    }

    lint {
        // 新代码引入 lint 问题（error 级）直接使构建失败；存量问题在基线文件中豁免。
        //  ./gradlew :app:lintDebug          —— 检查（pre-commit 钩子提交前自动执行）
        //  ./gradlew :app:updateLintBaseline —— 修复问题后重建基线（有意豁免时）
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
}

// Developer variant stays separate; public release versionCode is defined above.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { output -> output.versionCode.set(168) }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // CarPlay 风格桌面：分页网格
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // AirPlay 配对密码学：X25519/Ed25519/ChaCha20-Poly1305/HKDF-SHA512
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    // 隐形屏模式：shell 权限（无线调试激活，免 root）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
}
