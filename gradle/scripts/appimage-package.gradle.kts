
import java.net.URI
import java.io.File

val appimageToolUrl = "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
val toolCacheDir = File(rootProject.projectDir, ".gradle/tools")
val appimageToolFile = File(toolCacheDir, "appimagetool-x86_64.AppImage")

tasks.register("downloadAppImageTool") {
    group = "package"
    description = "Downloads appimagetool if not present."
    outputs.file(appimageToolFile)
    
    doLast {
        if (!appimageToolFile.exists()) {
            println("Downloading appimagetool from $appimageToolUrl...")
            toolCacheDir.mkdirs()
            URI(appimageToolUrl).toURL().openStream().use { input ->
                appimageToolFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            appimageToolFile.setExecutable(true)
        }
    }
}

fun Project.createAppImageTask(
    taskName: String,
    dependsOnTaskName: String,
    suffix: String = "",
    useReleaseJar: Boolean = false
) {
    tasks.register(taskName) {
        group = "package"
        description = "Packages the application as an AppImage${if (useReleaseJar) " (with ProGuard)" else " (No ProGuard)"}."
        
        val dependsOnTask = tasks.named(dependsOnTaskName)
        dependsOn("downloadAppImageTool", dependsOnTask)

        val appName = "WebrtcSamples"
        val appDir = layout.buildDirectory.dir("appimage/AppDir$suffix").get().asFile
        val outputDir = layout.buildDirectory.dir("appimage/output").get().asFile
        val outputFile = File(outputDir, "${appName}${suffix}-x86_64.AppImage")

        // Define inputs and outputs for incremental builds
        // Since we can't easily get the Jar task type here without imports, 
        // we'll use the directory as input for now, but filtered to the specific task
        inputs.files(dependsOnTask)
        inputs.file(appimageToolFile)
        outputs.file(outputFile)
        outputs.dir(appDir)
        
        doLast {
            appDir.deleteRecursively()
            appDir.mkdirs()
            outputDir.mkdirs()
            
            // Find the uberJar file in build/compose/jars/
            val jarsDir = layout.buildDirectory.dir("compose/jars").get().asFile
            val allJars = jarsDir.listFiles()?.filter { it.name.endsWith(".jar") } ?: emptyList<File>()
            
            val uberJarFile = if (useReleaseJar) {
                allJars.find { it.name.contains("-release") }
            } else {
                allJars.find { !it.name.contains("-release") }
            } ?: allJars.firstOrNull()
              ?: throw GradleException("UberJar not found in ${jarsDir.absolutePath}. Ensure $dependsOnTaskName task produced it.")
            
            println("Using UberJar for $taskName: ${uberJarFile.absolutePath}")

            // 1. Copy UberJar
            val binDir = File(appDir, "usr/bin")
            binDir.mkdirs()
            val targetJar = File(binDir, "${appName}.jar")
            uberJarFile.copyTo(targetJar)
            
            // 2. Create AppRun script
            val appRun = File(appDir, "AppRun")
            appRun.writeText("""
                #!/bin/bash
                SELF_PATH=${'$'}(readlink -f "${'$'}0")
                HERE=${'$'}(dirname "${'$'}SELF_PATH")
                exec java -jar "${'$'}{HERE}/usr/bin/${appName}.jar" "${'$'}@"
            """.trimIndent())
            appRun.setExecutable(true)
            
            // 3. Create .desktop file
            val desktopFile = File(appDir, "${appName}.desktop")
            desktopFile.writeText("""
                [Desktop Entry]
                Name=${appName}
                Exec=${appName}
                Icon=${appName}
                Type=Application
                Categories=Network;
            """.trimIndent())
            
            // 4. Placeholder Icon
            val iconFile = File(appDir, "${appName}.png")
            if (!iconFile.exists()) {
                iconFile.createNewFile() 
            }

            // 5. Run appimagetool
            println("Running appimagetool...")
            project.exec {
                commandLine(appimageToolFile.absolutePath, appDir.absolutePath, outputFile.absolutePath)
                environment("ARCH", "x86_64")
            }
            
            println("AppImage created at: ${outputFile.absolutePath}")
        }
    }
}

// Register both versions
project.createAppImageTask(
    taskName = "packageAppImage",
    dependsOnTaskName = "packageReleaseUberJarForCurrentOS",
    useReleaseJar = true
)

project.createAppImageTask(
    taskName = "packageAppImageNoProguard",
    dependsOnTaskName = "packageUberJarForCurrentOS",
    suffix = "-no-proguard",
    useReleaseJar = false
)
