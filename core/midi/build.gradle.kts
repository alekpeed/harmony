plugins {
    alias(libs.plugins.android.library)
}

/**
 * `core:midi` is an Android library because the production source talks to
 * `android.media.midi` (20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §4).
 *
 * Everything that decides *meaning* — byte-stream parsing, sustain semantics, which notes are
 * sounding — is plain Kotlin inside this module and runs as JVM unit tests. Only device
 * discovery and transport touch the platform, which is what makes the whole engine testable
 * without a keyboard plugged in (05_MIDI_INPUT_ENGINE.md §12).
 */

android {
    namespace = "com.harmonygates.core.midi"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        lintConfig = rootProject.file("config/lint/lint.xml")
    }
}

kotlin {
    explicitApi()
    jvmToolchain(libs.versions.javaToolchain.get().toInt())
}

dependencies {
    api(projects.core.music)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.testing)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
