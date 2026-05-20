import dependencies.Dep
import dependencies.Versions

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "info.nukoneko.android.nfcreader"
    compileSdk = Versions.androidCompileSdkVersion

    defaultConfig {
        applicationId = "info.nukoneko.android.nfcreader"
        minSdk = Versions.androidMinSdkVersion
        targetSdk = Versions.androidTargetSdkVersion
        versionCode = Versions.androidVersionCode
        versionName = Versions.androidVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = Versions.jvmTarget
    }

    sourceSets {
        getByName("main") { java.srcDirs("src/main/kotlin") }
        getByName("test") { java.srcDirs("src/test/kotlin") }
        getByName("androidTest") { java.srcDirs("src/androidTest/kotlin") }
    }
}

dependencies {
    implementation(Dep.Kotlin.reflect)
    implementation(Dep.AndroidX.coreKtx)
    implementation(Dep.AndroidX.activityCompose)
    implementation(Dep.AndroidX.lifecycleViewModelCompose)
    implementation(Dep.AndroidX.lifecycleRuntimeCompose)

    implementation(platform(Dep.AndroidX.composeBom))
    implementation(Dep.AndroidX.composeUi)
    implementation(Dep.AndroidX.composeUiToolingPreview)
    implementation(Dep.AndroidX.composeMaterial3)
    debugImplementation(Dep.AndroidX.composeUiTooling)

    testImplementation(Dep.Test.junit)
    testImplementation(Dep.Test.coroutinesTest)

    androidTestImplementation(platform(Dep.AndroidX.composeBom))
    androidTestImplementation(Dep.Test.testRunner)
    androidTestImplementation(Dep.Test.espressoCore)
}
