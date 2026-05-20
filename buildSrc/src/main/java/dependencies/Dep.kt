package dependencies

@Suppress("unused")
object Dep {
    object Test {
        const val junit = "junit:junit:4.13.2"
        const val testRunner = "androidx.test:runner:1.6.2"
        const val espressoCore = "androidx.test.espresso:espresso-core:3.6.1"
        const val coroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0"
    }

    object AndroidX {
        const val coreKtx = "androidx.core:core-ktx:1.15.0"
        const val activityCompose = "androidx.activity:activity-compose:1.9.3"
        const val lifecycleViewModelCompose =
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7"
        const val lifecycleRuntimeCompose =
            "androidx.lifecycle:lifecycle-runtime-compose:2.8.7"

        const val composeBom = "androidx.compose:compose-bom:${Versions.composeBomVersion}"
        const val composeUi = "androidx.compose.ui:ui"
        const val composeUiToolingPreview = "androidx.compose.ui:ui-tooling-preview"
        const val composeUiTooling = "androidx.compose.ui:ui-tooling"
        const val composeMaterial3 = "androidx.compose.material3:material3"
    }

    object Kotlin {
        const val version = Versions.kotlinVersion
        const val reflect = "org.jetbrains.kotlin:kotlin-reflect:$version"
    }
}
