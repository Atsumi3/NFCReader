import dependencies.Versions

plugins {
    id("com.android.application") version Versions.androidGradlePluginVersion apply false
    id("org.jetbrains.kotlin.android") version Versions.kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.compose") version Versions.kotlinVersion apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
