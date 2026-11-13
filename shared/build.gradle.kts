import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.skie)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

abstract class GenerateIllumidelSecretsTask : DefaultTask() {
    @get:Input
    abstract val githubToken: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = outputDir.get().asFile.resolve("ca/cgagnier/wlednativeandroid/shared")
        dir.mkdirs()
        val escaped =
            githubToken
                .get()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
        dir.resolve("IllumidelSecrets.kt").writeText(
            """
            package ca.cgagnier.wlednativeandroid.shared

            internal object IllumidelSecrets {
                const val GITHUB_TOKEN: String = "$escaped"
            }
            """.trimIndent() + "\n",
        )
    }
}

val localPropertiesToken =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { content ->
            val props = Properties()
            props.load(content.reader())
            props
                .getProperty("GITHUB_ILLUMIDEL_KEY")
                ?.trim()
                ?.removeSurrounding("\"")
                ?.trim()
                .orEmpty()
        }.orElse("")

val githubIllumidelKey =
    localPropertiesToken.zip(
        providers.environmentVariable("GITHUB_ILLUMIDEL_KEY").orElse(""),
    ) { fromFile, fromEnv ->
        fromFile.ifEmpty {
            fromEnv
                .trim()
                .removeSurrounding("\"")
                .trim()
        }
    }

val generateIllumidelSecrets =
    tasks.register<GenerateIllumidelSecretsTask>("generateIllumidelSecrets") {
        githubToken.set(githubIllumidelKey)
        outputDir.set(layout.buildDirectory.dir("generated/illumidelSecrets/commonMain/kotlin"))
    }

// CI sets -Pwled.ios.simulatorOnly=true to skip the device slice, which it never runs.
val iosSimulatorOnly = providers.gradleProperty("wled.ios.simulatorOnly").orNull == "true"

kotlin {
    android {
        namespace = "ca.cgagnier.wlednativeandroid.shared"
        compileSdk = 37
        minSdk = 24

        withHostTest {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val xcframework = XCFramework("Shared")

    listOfNotNull(
        if (iosSimulatorOnly) null else iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "ca.cgagnier.wlednativeandroid.shared")
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateIllumidelSecrets)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
            implementation(libs.semver)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.okio)
            api(libs.datastore.core)
            api(libs.datastore.core.okio)
            api(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
    }
}

room {
    schemaDirectory("$rootDir/app/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    if (!iosSimulatorOnly) {
        add("kspIosArm64", libs.androidx.room.compiler)
    }
    add("androidHostTestImplementation", libs.mockk)
}

skie {
    analytics {
        enabled.set(false)
    }
}

tasks.register("test") {
    description = "Runs unit tests for the Android host (including commonTest)."
    group = "verification"
    dependsOn("testAndroidHostTest")
}

val syncDebugXCFramework =
    tasks.register<Sync>("syncSharedDebugXCFramework") {
        dependsOn("assembleSharedDebugXCFramework")
        from(layout.buildDirectory.dir("XCFrameworks/debug/Shared.xcframework"))
        into(layout.buildDirectory.dir("XCFrameworks/Shared.xcframework"))
    }

val syncReleaseXCFramework =
    tasks.register<Sync>("syncSharedReleaseXCFramework") {
        dependsOn("assembleSharedReleaseXCFramework")
        from(layout.buildDirectory.dir("XCFrameworks/release/Shared.xcframework"))
        into(layout.buildDirectory.dir("XCFrameworks/Shared.xcframework"))
    }

tasks.named("assembleSharedDebugXCFramework") {
    finalizedBy(syncDebugXCFramework)
}
tasks.named("assembleSharedReleaseXCFramework") {
    finalizedBy(syncReleaseXCFramework)
}
