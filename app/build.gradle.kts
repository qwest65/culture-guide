import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
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
        versionCode = 5
        versionName = "0.5.0"
        buildConfigField("String", "MAPKIT_API_KEY", "\"$mapkitApiKey\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.yandex.android:maps.mobile:4.45.0-full")
}
