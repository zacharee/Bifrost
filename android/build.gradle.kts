import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

group = rootProject.extra["groupName"].toString()
version = rootProject.extra["versionName"].toString()

dependencies {
    implementation(project(":common"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

android {
    val packageName = rootProject.extra["packageName"].toString()
    this.compileSdk = rootProject.extra["compileSdk"].toString().toInt()

    defaultConfig {
        applicationId = packageName

        this.minSdk = rootProject.extra["minSdk"].toString().toInt()
        this.targetSdk = rootProject.extra["targetSdk"].toString().toInt()

        this.versionCode = rootProject.extra["versionCode"].toString().toInt()
        this.versionName = rootProject.extra["versionName"].toString()

        resValue("string", "app_name", "${rootProject.extra["appName"]}")
    }

    namespace = packageName

    buildFeatures {
        compose = true
        aidl = true
        resValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        val javaVersionEnum = rootProject.extra["javaVersionEnum"] as JavaVersion
        sourceCompatibility = javaVersionEnum
        targetCompatibility = javaVersionEnum
        isCoreLibraryDesugaringEnabled = true
    }

    lint {
        abortOnError = false
    }

    packaging {
        resources.excludes.add("META-INF/versions/9/previous-compilation-data.bin")
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(rootProject.extra["javaVersionEnum"].toString()))
    }
}

afterEvaluate {
    base {
        archivesName.set("bifrost_android_${android.defaultConfig.versionName}")
    }
}
