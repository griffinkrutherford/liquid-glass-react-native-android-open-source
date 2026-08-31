plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "io.github.griffinkrutherford.liquidglass.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.griffinkrutherford.liquidglass.sample"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":liquid-glass-view"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
}
