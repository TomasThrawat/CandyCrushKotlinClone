plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tomasthrawat.candycrush"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tomasthrawat.candycrush"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (!keystoreFile.isNullOrBlank()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}
