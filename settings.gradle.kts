@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()

        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://maven.hq.hydraulic.software")
        maven(File("${rootProject.projectDir.absolutePath}/libs/"))
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()

        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap/")
        maven("https://jitpack.io")
        maven(File("${rootProject.projectDir.absolutePath}/libs/"))
        maven("https://repo.jenkins-ci.org/public/")
    }
}

rootProject.name = "SamloaderKotlin"
include(":android")
include(":desktop")
include(":common")
