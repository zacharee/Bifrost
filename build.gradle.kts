extra["versionCode"] = 95
extra["versionName"] = "2.1.4"

extra["compileSdk"] = 37
extra["targetSdk"] = 36
extra["minSdk"] = 26

extra["javaVersionEnum"] = JavaVersion.VERSION_21

extra["groupName"] = "tk.zwander"
extra["packageName"] = "tk.zwander.samsungfirmwaredownloader"
extra["appName"] = "Bifrost"

extra["bugsnagJvmApiKey"] = "a5b9774e86bc615c2e49a572b8313489"
extra["bugsnagAndroidApiKey"] = "3e0ed592029da1d5cc9b52160ef702ea"

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.bugsnag.gradle) apply false
    alias(libs.plugins.buildkonfig) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.conveyor) apply false
    alias(libs.plugins.kotlin.atomicfu) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform.android.library) apply false
    alias(libs.plugins.kotlin.native.cocoapods) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.hot.reload) apply false
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xskip-prerelease-check", "-Xdont-warn-on-error-suppression")
    }
}

group = extra["groupName"].toString()
version = extra["versionName"].toString()
