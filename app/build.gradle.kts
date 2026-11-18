import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val releaseStoreFile = System.getenv("JARFORGE_KEYSTORE_PATH") ?: "missing-keystore.jks"
val releaseStorePassword = System.getenv("JARFORGE_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("JARFORGE_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("JARFORGE_KEY_PASSWORD").orEmpty()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jarforge.ide"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jarforge.ide"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-stage2"
    }

    signingConfigs {
        create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
}
