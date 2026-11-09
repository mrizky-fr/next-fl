import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Same numbering as Android
val commitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    // UI shared with Android
    sourceSets.main {
        kotlin.srcDir("../shared/src/main/kotlin")
    }
}

sourceSets.main {
    resources.srcDir("../app/src/main/res/font")
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.desktop.material3)
    implementation(libs.desktop.icons)
    implementation(libs.desktop.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jlayer)
    // Modern Windows file dialog
    implementation(libs.filekit.dialogs)
    testImplementation(libs.kotlin.test)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Installer JDK must ship jpackage
val packaging = gradle.startParameter.taskNames.any { it.contains("package", ignoreCase = true) || it.contains("Distributable") }
val packagingJdk = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

compose.desktop {
    application {
        mainClass = "id.nextfl.desktop.MainKt"
        if (packaging) javaHome = packagingJdk.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("-Dnextfl.version=0.1.$commitCount")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "NextFL"
            packageVersion = "1.0.$commitCount"
            description = "NextFL DAW dan visualizer"
            vendor = "DealApps"
            modules("java.desktop", "java.logging", "jdk.unsupported")
            windows {
                menuGroup = "NextFL"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "4B7D3E61-2C58-4E0A-9B1F-6A3C8D2E7F10"
                iconFile.set(project.file("icon/nextfl.ico"))
            }
        }
        // Drops unused icons and classes
        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}

// Extract and run, no install
val packageReleasePortable by tasks.registering(Zip::class) {
    group = "compose desktop"
    description = "Zips the release app image as a portable build."
    dependsOn("createReleaseDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main-release/app"))
    archiveFileName.set("NextFL-Portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main-release/portable"))
}
