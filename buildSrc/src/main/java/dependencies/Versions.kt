package dependencies

object Versions {
    const val androidCompileSdkVersion = 36
    const val androidTargetSdkVersion = 36
    const val androidMinSdkVersion = 21

    private const val versionMajor = 2
    private const val versionMinor = 0
    private const val versionPatch = 0
    private const val versionOffset = 0
    const val androidVersionCode =
        (versionMajor * 10000 + versionMinor * 100 + versionPatch) * 100 + versionOffset

    const val androidVersionName = "$versionMajor.$versionMinor.$versionPatch"

    const val jvmTarget = "17"

    const val androidGradlePluginVersion = "8.11.1"
    const val kotlinVersion = "2.1.0"
    const val composeBomVersion = "2024.12.01"
}
