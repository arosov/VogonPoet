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

    val appVersion = version.toString()
    // Use rootProject.file to target the root output directory where Conveyor generates files
    val downloadHtml = rootProject.file("output/download.html")
    val destAppImageName = "ovh-devcraft-vogonpoet-$appVersion-x86_64.AppImage"
    val destAppImage = rootProject.file("output/$destAppImageName")

    doLast {
        if (!downloadHtml.exists()) {
            println("Warning: output/download.html not found, skipping AppImage update")
            return@doLast
        }

        if (!destAppImage.exists()) {
            println("Warning: AppImage not found at ${destAppImage.absolutePath}. Ensure Conveyor has run.")
            return@doLast
        }

        var html = downloadHtml.readText()

        val appImageButton =
            """
            <a href="https://github.com/arosov/VogonPoet/releases/latest/download/$destAppImageName" class="download-button linux-amd64" download>
                <svg xmlns="http://www.w3.org/2000/svg" width="2em" height="2em" fill="currentColor" class="bi bi-tux" viewBox="0 0 16 16" style="margin-right: 0.8em;">
                    <path d="M8.996 4.497c.104-.076.1-.168.186-.158s.022.102-.098.207c-.12.104-.308.243-.46.323-.291.152-.631.336-.993.336s-.647-.167-.853-.33c-.102-.082-.186-.162-.248-.221-.11-.086-.096-.207-.052-.204.075.01.087.109.134.153.064.06.144.137.241.214.195.154.454.304.778.304s.702-.19.932-.32c.13-.073.297-.204.433-.304M7.34 3.781c.055-.02.123-.031.174-.003.011.006.024.021.02.034-.012.038-.074.032-.11.05-.032.017-.057.052-.093.054-.034 0-.086-.012-.09-.046-.007-.044.058-.072.1-.089m.581-.003c.05-.028.119-.018.173.003.041.017.106.045.1.09-.004.033-.057.046-.09.045-.036-.002-.062-.037-.093-.053-.036-.019-.098-.013-.11-.051-.004-.013.008-.028.02-.034"/>
                    <path fill-rule="evenodd" d="M8.446.019c2.521.003 2.38 2.66 2.364 4.093-.01.939.509 1.574 1.04 2.244.474.56 1.095 1.38 1.45 2.32.29.765.402 1.613.115 2.465a.8.8 0 0 1 .254.152l.001.002c.207.175.271.447.329.698.058.252.112.488.224.615.344.382.494.667.48.922-.015.254-.203.43-.435.57-.465.28-1.164.491-1.586 1.002-.443.527-.99.83-1.505.871a1.25 1.25 0 0 1-1.256-.716v-.001a1 1 0 0 1-.078-.21c-.67.038-1.252-.165-1.718-.128-.687.038-1.116.204-1.506.206-.151.331-.445.547-.808.63-.5.114-1.126 0-1.743-.324-.577-.306-1.31-.278-1.85-.39-.27-.057-.51-.157-.626-.384-.116-.226-.067-.532.062-.852.13-.327.35-.735.52-1.183.166-.44.32-.93.49-1.474.19-.6.47-1.34.47-2.14 0-1.27.01-3.51 2.36-4.09 1.15-.3 2.29-.63 2.48-1.5 0-.7-.34-1.32-.5-1.61-.16-.29-.28-.39-.28-.51 0-.13.09-.2 1.02-.18zM5.006 5.117c-.85.77-1.96 3.11-2.31 5.31-.35 2.2.03 3.94.92 3.94.88 0 1.44-1.44 1.8-3.64.36-2.2.44-4.84-.41-5.61zM11.006 5.117c.85.77 1.96 3.11 2.31 5.31.35 2.2-.03 3.94-.92 3.94-.88 0-1.44-1.44-1.8-3.64-.36-2.2-.44-4.84.41-5.61z"/>
                </svg>
                <span>Download AppImage for AMD64</span>
            </a>
            """.trimIndent()

        // Insert AppImage button after the .deb button, before the linux-all div (with <br><br> like macOS)
        val linuxAllPattern = """(<div class="linux-all"><br></div>)""".toRegex()
        html = html.replaceFirst(linuxAllPattern, "<br><br>\n$appImageButton\n                $1")

        downloadHtml.writeText(html)
        println("Updated download.html to include AppImage")
    }
}
