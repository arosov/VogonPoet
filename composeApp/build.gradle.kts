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

// --- Update Download Page for AppImage ---
tasks.register("updateDownloadPageForAppImage") {
    group = "vogonpoet"
    description = "Updates the download.html to include AppImage download option"

    dependsOn("packageAppImage")

    val appVersion = version.toString()
    // Use rootProject.file to target the root output directory where Conveyor generates files
    val downloadHtml = rootProject.file("output/download.html")
    val appImageOutputDir =
        layout.buildDirectory
            .dir("appimage/output")
            .get()
            .asFile
    val appImageFile = File(appImageOutputDir, "VogonPoet-x86_64.AppImage")

    doLast {
        if (!downloadHtml.exists()) {
            println("Warning: output/download.html not found, skipping AppImage update")
            return@doLast
        }

        if (!appImageFile.exists()) {
            println("Warning: AppImage not found at ${appImageFile.absolutePath}, skipping AppImage update")
            return@doLast
        }

        // Copy AppImage to output directory for release artifacts
        val destAppImageName = "ovh-devcraft-vogonpoet-$appVersion-x86_64.AppImage"
        val destAppImage = rootProject.file("output/$destAppImageName")
        appImageFile.copyTo(destAppImage, overwrite = true)
        println("Copied AppImage to ${destAppImage.absolutePath}")

        var html = downloadHtml.readText()

        val appImageButton =
            """
            <a href="https://github.com/arosov/VogonPoet/releases/latest/download/$destAppImageName" class="download-button linux-amd64" download>
                <svg width="2em" height="2em" viewBox="0 0 256 315" version="1.1" xmlns="http://www.w3.org/2000/svg"
                     xmlns:xlink="http://www.w3.org/1999/xlink" preserveAspectRatio="xMidYMid">
                    <g>
                        <path d="M152.796616,167.425369 C147.545671,167.498421 153.790117,170.131199 160.645273,171.186063 C162.538769,169.7075 164.256942,168.211404 165.788102,166.756218 C161.51897,167.802315 157.173864,167.825692 152.796616,167.425369"/>
                        <path d="M180.979901,160.400733 C184.106507,156.084848 186.385715,151.359874 187.189282,146.474187 C186.487987,149.957285 184.597414,152.964086 182.817877,156.137446 C173.002673,162.317606 181.894506,152.467336 182.812034,148.724175 C172.257546,162.007867 181.362691,156.689715 180.979901,160.400733"/>
                        <path d="M191.382441,133.330754 C192.016528,123.874962 189.521087,126.864232 188.682455,130.472977 C189.661347,130.981416 190.435693,137.1382 191.382441,133.330754"/>
                    </g>
                </svg>
                <span>Download AppImage for AMD64</span>
            </a>
            """.trimIndent()

        // Insert AppImage button after the .deb button, before the linux-all div (with <br><br> like macOS)
        val linuxAllPattern = """(<div class="linux-all"><br></div>)""".toRegex()
        html = html.replaceFirst(linuxAllPattern, "<br><br>\n$appImageButton\n                $1")

        // Also add AppImage as an alternative format link (like .tar.gz)
        val tarGzLinkPattern =
            """(<a class="linux-amd64" href="https://github\.com/arosov/VogonPoet/releases/latest/download/ovh-devcraft-vogonpoet-$appVersion-linux-amd64\.tar\.gz"[^>]*>[^<]*</a>)"""
                .toRegex()
        val appImageLink =
            """<a class="linux-amd64" href="https://github.com/arosov/VogonPoet/releases/latest/download/$destAppImageName" download>Download .AppImage</a>"""
        html = html.replaceFirst(tarGzLinkPattern, "$1\n                        \n                        $appImageLink")

        downloadHtml.writeText(html)
        println("Updated download.html to include AppImage")
    }
}

// Make updateDownloadPageForAppImage run after packageAppImage
tasks.matching { it.name == "updateDownloadPageForAppImage" }.configureEach {
    dependsOn("packageAppImage")
}
