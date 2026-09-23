import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use { localProperties.load(it) }
val mapkitApiKey = System.getenv("MAPKIT_API_KEY")
    ?.takeIf { it.isNotBlank() }
    ?: localProperties.getProperty("MAPKIT_API_KEY", "")

android {
    namespace = "ru.cultureguide"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.cultureguide"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.8.0"
        buildConfigField("String", "MAPKIT_API_KEY", "\"$mapkitApiKey\"")
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.yandex.android:maps.mobile:4.45.0-full")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.8.3")
    implementation("androidx.compose.foundation:foundation:1.8.3")
    implementation("androidx.compose.runtime:runtime:1.8.3")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    testImplementation("junit:junit:4.13.2")
}
