plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "io.github.griffinkrutherford.liquidglass"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":liquid-glass-core"))
    testImplementation("junit:junit:4.13.2")
}
