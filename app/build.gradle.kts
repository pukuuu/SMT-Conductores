plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cl.smt.conductores"
    compileSdk = 36

    defaultConfig {
        applicationId = "cl.smt.conductores"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "2.0.1s"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
}