import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.core)
    // Pure-Java LAME, LGPL
    implementation(libs.jump3r)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
}
