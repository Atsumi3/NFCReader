package dependencies

@Suppress("unused")
object Dep {
    object GradlePlugin {
        const val android = "com.android.tools.build:gradle:${Versions.androidGradlePluginVersion}"
        const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Kotlin.version}"
    }

    object Test {
        const val junit = "junit:junit:4.13.2"
        const val testRunner = "androidx.test:runner:1.6.2"
        const val espressoCore = "androidx.test.espresso:espresso-core:3.6.1"
        const val robolectric = "org.robolectric:robolectric:4.14"
        const val mockito = "org.mockito.kotlin:mockito-kotlin:5.4.0"
    }

    object AndroidX {
        const val appCompat = "androidx.appcompat:appcompat:1.7.0"
        const val recyclerView = "androidx.recyclerview:recyclerview:1.3.2"
        const val design = "com.google.android.material:material:1.12.0"
        const val coreKtx = "androidx.core:core-ktx:1.15.0"
        const val activityKtx = "androidx.activity:activity-ktx:1.9.3"
        const val lifecycleViewModel = "androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7"
        const val lifecycleLiveData = "androidx.lifecycle:lifecycle-livedata-ktx:2.8.7"
    }

    object Kotlin {
        const val version = Versions.kotlinVersion
        const val reflect = "org.jetbrains.kotlin:kotlin-reflect:$version"
    }
}
