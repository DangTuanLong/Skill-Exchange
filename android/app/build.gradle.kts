import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.skillexchange.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.skillexchange.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Reading local.properties file if present
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { stream ->
            localProperties.load(stream)
        }
    }

    val overrideBaseUrl = localProperties.getProperty("API_BASE_URL")
        ?: project.findProperty("API_BASE_URL")?.toString()
        ?: System.getenv("API_BASE_URL")

    buildTypes {
        debug {
            val debugUrl = overrideBaseUrl?.takeIf { it.isNotBlank() } ?: "http://10.0.2.2:8080"
            buildConfigField("String", "BASE_URL", "\"$debugUrl\"")
        }
        release {
            optimization {
                enable = false
            }
            val releaseUrl = overrideBaseUrl?.takeIf { it.isNotBlank() }
            val isReleaseBuildRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            if (releaseUrl != null) {
                buildConfigField("String", "BASE_URL", "\"$releaseUrl\"")
            } else if (isReleaseBuildRequested) {
                throw GradleException("Release build requires API_BASE_URL to be set in local.properties (-PAPI_BASE_URL=... or environment variable API_BASE_URL)!")
            } else {
                buildConfigField("String", "BASE_URL", "\"UNSET_RELEASE_API_BASE_URL\"")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.auth)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}