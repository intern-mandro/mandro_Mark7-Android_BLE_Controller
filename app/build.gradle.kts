plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mandro.mark7"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mandro.mark7"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("Boolean", "USE_MOCK_BLE", "false")
        }
        debug {
            // 실기기 없이 "연결된 상태"로 앱을 돌린다 (FakeHandRepository 바인딩).
            buildConfigField("Boolean", "USE_MOCK_BLE", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlin {
        jvmToolchain(21)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    // ── Jetpack Compose ───────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.compose.viewmodel)
    implementation(libs.compose.navigation)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // ── Architecture ──────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // ── DI: Hilt ─────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── 로컬 저장 ─────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // ── 제스처 사진 로딩 ──────────────────────────────────────
    implementation(libs.coil.compose)

    // ── 테스트 ────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test)
}
