plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "kz.kkm"
    compileSdk = 34

    defaultConfig {
        applicationId = "kz.kkm.cashregister"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Room schema export
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Certificate pinning — replace with real OFD + ISNA pins
            buildConfigField("String", "OFD_BASE_URL", "\"https://ofd.kgd.gov.kz/api/v2/\"")
            buildConfigField("String", "ISNA_BASE_URL", "\"https://is.kgd.gov.kz/api/v1/\"")
            buildConfigField("String", "OFD_PIN_SHA256", "\"sha256/REPLACE_WITH_REAL_OFD_CERT_HASH=\"")
            buildConfigField("String", "ISNA_PIN_SHA256", "\"sha256/REPLACE_WITH_REAL_ISNA_CERT_HASH=\"")
        }
        debug {
            isDebuggable = true
            buildConfigField("String", "OFD_BASE_URL", "\"https://test.ofd.kgd.gov.kz/api/v2/\"")
            buildConfigField("String", "ISNA_BASE_URL", "\"https://test.is.kgd.gov.kz/api/v1/\"")
            buildConfigField("String", "OFD_PIN_SHA256", "\"sha256/\"")
            buildConfigField("String", "ISNA_PIN_SHA256", "\"sha256/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room + SQLCipher
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // Security
    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.workmanager.ktx)

    // QR + Barcode
    implementation(libs.zxing.core)
    implementation(libs.mlkit.barcode)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
}
