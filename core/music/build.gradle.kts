plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * `core:music` is deliberately a plain Kotlin/JVM library.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §1: no Android dependency may leak in here. Keeping the
 * module off the Android plugin makes that a build-level guarantee rather than a
 * convention, and keeps the several-thousand theory assertions running as fast JVM tests.
 */

kotlin {
    // The domain layer is consumed by every feature module; an accidental widening of the
    // public surface is a real cost, so the compiler polices it.
    explicitApi()
    jvmToolchain(libs.versions.javaToolchain.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
    }
}
