import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// CuckooChess, a Java engine that runs inside the app's own process. See README.md for where
// the vendored sources come from.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    // The vendored sources are kept as they are upstream; their warnings are not ours to fix.
    options.compilerArgs.add("-nowarn")
    options.encoding = "UTF-8"
}

dependencies {
    api(project(":core:engine"))

    testImplementation(libs.junit)
}
