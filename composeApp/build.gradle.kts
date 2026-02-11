import net.pwall.json.kotlin.codegen.gradle.JSONSchemaCodegen
import net.pwall.json.kotlin.codegen.gradle.JSONSchemaCodegenPlugin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
}

apply<JSONSchemaCodegenPlugin>()

kotlin {
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/sources/json-kotlin"))
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.websockets)
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

compose.desktop {
    application {
        mainClass = "ovh.devcraft.vogonpoet.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ovh.devcraft.vogonpoet"
            packageVersion = "1.0.0"
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
