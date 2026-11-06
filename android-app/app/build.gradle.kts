plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zeit2206.speakup"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zeit2206.speakup"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // Default server token = the PC server's token.txt, so the phone connects without typing it.
        val token = rootProject.file("../server/token.txt").takeIf { it.exists() }?.readText()?.trim() ?: ""
        buildConfigField("String", "SERVER_TOKEN", "\"$token\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the APK installs directly; swap for a real key before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
}
