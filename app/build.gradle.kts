plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.englishbuddy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.englishbuddy"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Klucz z GitHub Secrets (zmienna środowiskowa w Actions). Może być pusty – wtedy wpisujesz go w aplikacji.
        val key = System.getenv("ANTHROPIC_API_KEY") ?: ""
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$key\"")
        val gemini = System.getenv("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$gemini\"")
        val tavus = System.getenv("TAVUS_API_KEY") ?: ""
        buildConfigField("String", "TAVUS_API_KEY", "\"$tavus\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
