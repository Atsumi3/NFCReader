import dependencies.Dep
import dependencies.Versions

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
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
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = Versions.jvmTarget
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin", "src/sharedTest/java", "src/sharedTest/kotlin")
            resources.srcDirs("src/sharedTest/resources")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin", "src/sharedTest/java", "src/sharedTest/kotlin")
            resources.srcDirs("src/sharedTest/resources")
        }
    }
}

dependencies {
    implementation(Dep.Kotlin.reflect)
    implementation(Dep.AndroidX.appCompat)
    implementation(Dep.AndroidX.recyclerView)
    implementation(Dep.AndroidX.design)
    implementation(Dep.AndroidX.coreKtx)
    implementation(Dep.AndroidX.activityKtx)
    implementation(Dep.AndroidX.lifecycleViewModel)
    implementation(Dep.AndroidX.lifecycleLiveData)

    testImplementation(Dep.Test.junit)
    testImplementation(Dep.Test.robolectric)
    testImplementation(Dep.Test.mockito)

    androidTestImplementation(Dep.Test.testRunner)
    androidTestImplementation(Dep.Test.espressoCore)
}
