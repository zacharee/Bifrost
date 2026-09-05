import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.gradle.kotlin.dsl.androidComponents
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.bugsnag.gradle)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.multiplatform.android.library)
    alias(libs.plugins.kotlin.native.cocoapods)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.moko.resources)
}


group = rootProject.extra["groupName"].toString()
version = rootProject.extra["versionName"].toString()

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.RequiresOptIn")
}

val javaVersionEnum: JavaVersion = rootProject.extra["javaVersionEnum"] as JavaVersion

kotlin {
    jvmToolchain(javaVersionEnum.toString().toInt())

    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()

    val versionCode: Int = rootProject.extra["versionCode"] as Int
    val versionName: String = rootProject.extra["versionName"] as String
    val packageName: String = rootProject.extra["packageName"] as String

    listOf(iosArm64, iosSimulatorArm64).forEach {
        it.compilations.getByName("main") {
            cinterops.create("BugsnagSamloader") {
                includeDirs("$projectDir/src/nativeInterop/cinterop/Bugsnag")
                definitionFile.set(file("$projectDir/src/nativeInterop/cinterop/Bugsnag.def"))
            }
        }
        it.binaries {
            framework {
                isStatic = true
                binaryOption("bundleVersion", versionCode.toString())
                binaryOption(
                    "bundleShortVersionString",
                    versionName,
                )
                binaryOption("bundleId", packageName)
                export(libs.nsexceptionKt.core)
            }
        }
    }

    android {
        withJava()

        val compileSdk: Int = rootProject.extra["compileSdk"] as Int
        val minSdk: Int = rootProject.extra["minSdk"] as Int

        this.compileSdk = compileSdk
        this.minSdk = minSdk

        androidResources {
            enable = true
        }

        namespace = "tk.zwander.common"

        this.enableCoreLibraryDesugaring = true

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersionEnum.toString()))
        }

//        buildFeatures {
//            aidl = true
//        }

//        sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
//        sourceSets["main"].res.srcDirs("src/androidMain/res")
//        sourceSets["main"].res.srcDir(layout.buildDirectory.file("generated/moko/androidMain/res"))
    }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.fromTarget(javaVersionEnum.toString())
                }
            }
        }
    }

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xdont-warn-on-error-suppression")
                }
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xdont-warn-on-error-suppression")
    }

    cocoapods {
        version = versionCode.toString()
        summary = "Bifrost"
        homepage = "https://zwander.dev"
        ios.deploymentTarget = "15.0"
        osx.deploymentTarget = "10.13"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "common"
            isStatic = true
            export(libs.moko.resources)
            export(libs.nsexceptionKt.core)

            binaryOption("bundleVersion", versionCode.toString())
            binaryOption(
                "bundleShortVersionString",
                versionName,
            )
            binaryOption("bundleId", packageName)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.compose.constraintlayout)
                api(libs.compose.foundation)
                api(libs.compose.material3)
                api(libs.compose.runtime)
                api(libs.compose.ui)
                api(libs.material.icons.core)
                api(libs.kotlin)
                api(libs.kotlin.reflect)
                api(libs.kotlinx.coroutines)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.serialization.json)
                api(libs.ksoup)
                api(libs.ktor.client.auth)
                api(libs.ktor.client.core)
                api(libs.ktor.client.cio)
                api(libs.moko.resources)
                api(libs.moko.resources.compose)
                api(libs.multiplatformSettings)
                api(libs.multiplatformSettings.noArg)
                api(libs.richeditor.compose)
                api(libs.semver)
                api(libs.filekit.core)
                api(libs.filekit.dialogs.compose)
                api(libs.kmpfile)
                api(libs.kmpplatform)
                api(libs.zwander.composedialog)
                api(libs.zwander.materialyou)
                api(libs.csv)
                api(libs.cryptography.core)
                api(libs.kotlinx.crypto.crc32)
                api(libs.kotlinx.atomicfu)
                api(libs.androidx.performance.annotation)
                api(libs.xmlbuilder)
                api(libs.ketch.core)
                api(libs.ketch.ktor)
                api(libs.ketch.sqlite)
            }
        }

        val androidAndJvmMain = create("androidAndJvmMain") {
            dependsOn(commonMain.get())

            dependencies {
//                api(libs.ktor.client.okhttp)
                api(libs.cryptography.provider.jdk)
            }
        }

        val skiaMain = create("skiaMain") {
            dependsOn(commonMain.get())
        }

        jvmMain {
            dependsOn(androidAndJvmMain)
            dependsOn(skiaMain)

            dependencies {
                api(compose.desktop.currentOs)
                api(libs.bugsnag.jvm)
                api(libs.flatlaf)
                api(libs.jna)
                api(libs.jna.platform)
                api(libs.jsystemthemedetector)
                api(libs.kotlinx.coroutines.swing)
                api(libs.oshi.core)
                api(libs.slf4j)
                api(libs.window.styler)
                api(libs.conveyor.control)
                api(libs.appdirs)
            }
        }

        androidMain {
            dependsOn(androidAndJvmMain)

            dependencies {
                api(libs.androidx.activity.compose)
                api(libs.androidx.core.ktx)
                api(libs.androidx.documentfile)
                api(libs.androidx.preference.ktx)
                api(libs.bugsnag.android)
                api(libs.google.material)
                api(libs.kotlinx.coroutines.android)
                api(libs.github.api)
                api(libs.filepicker)
            }
        }

        val darwinMain = create("darwinMain") {
            dependsOn(skiaMain)
            dependencies {
                api(libs.nsexceptionKt.core)
                api(libs.nserrorKt)
                api(libs.cryptography.provider.openssl3.prebuilt)
                api(libs.ktor.client.darwin)
            }
        }

        val iosMain = create("iosMain") {
            dependsOn(darwinMain)
        }
        iosArm64Main {
            dependsOn(iosMain)
            resources.srcDirs("build/generated/moko/iosArm64Main/src")
        }
        iosSimulatorArm64Main {
            dependsOn(iosMain)
            resources.srcDirs("build/generated/moko/iosSimulatorArm64Main/src")
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory("generated/moko/androidMain/res")
    }
}

buildkonfig {
    packageName = "tk.zwander.common"
    objectName = "GradleConfig"
    exposeObjectWithName = objectName

    defaultConfigs {
        buildConfigField(STRING, "versionName", "${rootProject.extra["versionName"]}")
        buildConfigField(STRING, "versionCode", "${rootProject.extra["versionCode"]}")
        buildConfigField(STRING, "appName", "${rootProject.extra["appName"]}")
        buildConfigField(STRING, "bugsnagJvmApiKey", "${rootProject.extra["bugsnagJvmApiKey"]}")
        buildConfigField(STRING, "bugsnagAndroidApiKey", "${rootProject.extra["bugsnagAndroidApiKey"]}")
    }
}

multiplatformResources {
    resourcesPackage.set("tk.zwander.samloaderkotlin.resources")
}

tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

afterEvaluate {
    val versionName: String = rootProject.extra["versionName"] as String
    val versionCode: Int = rootProject.extra["versionCode"] as Int

    val setVersionName = providers.exec {
        isIgnoreExitValue = true

        commandLine(
            "/usr/bin/plutil",
            "-replace",
            "CFBundleShortVersionString",
            "-string",
            versionName,
            "${rootProject.layout.projectDirectory.asFile.absolutePath}/iosApp/iosApp/Info.plist",
        )
    }

    val setVersionCode = providers.exec {
        isIgnoreExitValue = true

        commandLine(
            "/usr/bin/plutil",
            "-replace",
            "CFBundleVersion",
            "-string",
            "$versionCode",
            "${rootProject.layout.projectDirectory.asFile.absolutePath}/iosApp/iosApp/Info.plist",
        )
    }

    try {
        setVersionName.result.get()
        setVersionCode.result.get()

        setVersionName.standardError.asText.get().takeIf { it.isNotBlank() }?.let {
            println(it)
        }
        setVersionCode.standardError.asText.get().takeIf { it.isNotBlank() }?.let {
            println(it)
        }
    } catch (_: Throwable) {}
}
