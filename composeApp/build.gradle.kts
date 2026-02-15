import net.pwall.json.kotlin.codegen.gradle.JSONSchemaCodegen
import net.pwall.json.kotlin.codegen.gradle.JSONSchemaCodegenPlugin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

buildscript {
    dependencies {
        classpath(libs.json.kotlin.gradle)
        classpath(libs.json.kotlin.schema.codegen)
    }
}


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    id("dev.hydraulic.conveyor") version "1.13"
}

version = "0.0.1"

apply<JSONSchemaCodegenPlugin>()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// --- Babelfish Bundling Task ---
tasks.register("bundleBabelfish") {
    group = "vogonpoet"
    description = "Zips the babelfish backend for distribution"

    val babelfishDir = file("../../babelfish")
    val outputDir = file("src/jvmMain/resources")
    val outputFile = file("$outputDir/babelfish.zip")

    inputs.dir(babelfishDir)
    outputs.file(outputFile)

    doLast {
        if (!babelfishDir.exists()) {
            throw GradleException("Babelfish directory not found at ${babelfishDir.absolutePath}")
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        println("Bundling Babelfish from ${babelfishDir.absolutePath}...")

        var totalUncompressedSize: Long = 0
        val zipOut = ZipOutputStream(outputFile.outputStream())
        var version = version.toString()

        // 1. Zip the backend source
        babelfishDir.walkTopDown().forEach { file ->
            val relativePath = file.relativeTo(babelfishDir).path

            if (relativePath == "pyproject.toml") {
                val versionLine = file.readLines().find { it.trim().startsWith("version =") }
                version = versionLine
                    ?.split("=")
                    ?.get(1)
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.removeSurrounding("'") ?: "0.0.0"
            }

            // Exclude heavy/unnecessary dirs
            val isExcluded =
                relativePath.startsWith(".venv") ||
                    relativePath.startsWith("models") ||
                    relativePath.startsWith("tmp_extraction") ||
                    relativePath.startsWith(".git") ||
                    relativePath.contains("__pycache__") ||
                    relativePath.startsWith("tests") ||
                    relativePath.endsWith(".log") ||
                    relativePath.isEmpty()

            if (!isExcluded && file.isFile) {
                totalUncompressedSize += file.length()
                zipOut.putNextEntry(ZipEntry(relativePath))
                file.inputStream().use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }
        }

        // 2. Also include the bootstrap script into the zip at scripts/bootstrap.py
        val bootstrapFile = file("src/jvmMain/resources/scripts/bootstrap.py")
        if (bootstrapFile.exists()) {
            totalUncompressedSize += bootstrapFile.length()
            zipOut.putNextEntry(ZipEntry("scripts/bootstrap.py"))
            bootstrapFile.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }

        zipOut.close()

        file("$outputDir/babelfish_version.txt").writeText(version)

        val compressedSize = outputFile.length()
        println("Babelfish bundled successfully (v$version):")
        println("  - Compressed size: ${compressedSize / 1024} KB")
        println("  - Uncompressed size: ${totalUncompressedSize / 1024} KB")
    }
}

// Ensure bundleBabelfish runs before resources are processed
// Disabled for now to prevent accidental regeneration as per user request
/*
tasks.configureEach {
    if (name == "jvmProcessResources") {
        dependsOn("bundleBabelfish")
    }
}
*/

kotlin {
    jvm()

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.get().compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sources/json-kotlin"))
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            implementation(libs.lwjgl)
            implementation(libs.lwjgl.tinyfd)
            runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:natives-linux")
            runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:natives-windows")
            runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:natives-macos")
            runtimeOnly("org.lwjgl:lwjgl-tinyfd:${libs.versions.lwjgl.get()}:natives-linux")
            runtimeOnly("org.lwjgl:lwjgl-tinyfd:${libs.versions.lwjgl.get()}:natives-windows")
            runtimeOnly("org.lwjgl:lwjgl-tinyfd:${libs.versions.lwjgl.get()}:natives-macos")
        }
    }
}

dependencies {
    // Use the configurations created by the Conveyor plugin to tell Gradle/Conveyor where to find the artifacts for each platform.
    linuxAmd64(compose.desktop.linux_x64)
    macAmd64(compose.desktop.macos_x64)
    macAarch64(compose.desktop.macos_arm64)
    windowsAmd64(compose.desktop.windows_x64)
}

compose.desktop {
    application {
        mainClass = "ovh.devcraft.vogonpoet.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ovh.devcraft.vogonpoet"
            packageVersion = "1.0.0"

            buildTypes.release.proguard {
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }
    }
}

configure<JSONSchemaCodegen> {
    configFile.set(file("src/commonMain/resources/codegen-config.json"))
    inputs {
        inputFile(file("src/commonMain/resources/schema"))
    }
    outputDir.set(
        layout.buildDirectory
            .dir("generated/sources/json-kotlin")
            .get()
            .asFile,
    )
}

apply(from = "../gradle/scripts/appimage-package.gradle.kts")

// --- Generate Download Page from Template ---
tasks.register("generateDownloadPage") {
    group = "vogonpoet"
    description = "Generates download.html from template, replacing version placeholders"

    val appVersion = version.toString()
    val templateFile = project.file("src/jvmMain/resources/download-template.html")
    val outputFile = rootProject.file("output/download.html")

    doLast {
        if (!templateFile.exists()) {
            println("Warning: download-template.html not found at ${templateFile.absolutePath}")
            return@doLast
        }

        var template = templateFile.readText()

        // Replace @VERSION@ placeholder with actual version
        template = template.replace("@VERSION@", appVersion)

        // Ensure output directory exists
        outputFile.parentFile?.mkdirs()

        outputFile.writeText(template)
        println("Generated download.html with version $appVersion")
    }
}
