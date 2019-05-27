package dependencies

@Suppress("unused")
object Dep {
    object GradlePlugin {
        const val android = "com.android.tools.build:gradle:3.4.1"
        const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Kotlin.version}"
    }

    object Test {
        const val junit = "junit:junit:4.12"
        const val testRunner = "androidx.test:runner:1.1.0"
        const val espressoCore = "androidx.test.espresso:espresso-core:3.1.1"
        const val robolectric = "org.robolectric:robolectric:4.2"
    }

    object AndroidX {
        const val appCompat = "androidx.appcompat:appcompat:1.0.0"
        const val recyclerView = "androidx.recyclerview:recyclerview:1.0.0"
        const val design = "com.google.android.material:material:1.0.0"

        const val lifecycleExtensions = "androidx.lifecycle:lifecycle-extensions:2.0.0"
        const val lifecycleLiveData = "androidx.lifecycle:lifecycle-livedata:2.0.0"
    }

    object Kotlin {
        const val version = "1.3.31"
        const val stdlibJvm = "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$version"
        const val reflect = "org.jetbrains.kotlin:kotlin-reflect:$version"
    }
}