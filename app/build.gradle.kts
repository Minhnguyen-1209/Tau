plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val run: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.cuatoi.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cuatoi.app"
        minSdk = 26
        targetSdk = 34
        versionCode = run
        versionName = "2.$run"
    }

    // Khoá ký cố định: mọi bản build đều cùng chữ ký nên cài đè được và giữ nguyên dữ liệu.
    signingConfigs {
        create("tau") {
            storeFile = file("tau.jks")
            storePassword = "tau-personal"
            keyAlias = "tau"
            keyPassword = "tau-personal"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("tau")
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
