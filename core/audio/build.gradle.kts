plugins {
    alias(libs.plugins.android.library)
}

/**
 * `core:audio` is the sampler.
 *
 * 09_AUDIO_SAMPLER_ENGINE.md §2 is a hard constraint: "V1 must not require C++/NDK. Implement
 * the sampler around Android `AudioTrack` with a Kotlin mixer." So everything that decides what
 * a buffer contains — voice allocation, resampling, envelopes, the limiter — is plain Kotlin
 * inside this module and runs as JVM unit tests. Only the sink touches the platform.
 *
 * §1 is the other half of the contract: this is "not the source of truth for the player's
 * answer; MIDI is". Nothing here evaluates anything.
 */

android {
    namespace = "com.harmonygates.core.audio"
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
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
