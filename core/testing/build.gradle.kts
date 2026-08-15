plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * Test helpers only.
 *
 * 10_ANDROID_ARCHITECTURE.md §2 places this at the bottom of the graph and marks it "test
 * helpers only". Nothing here ships in the app: modules depend on it with `testImplementation`.
 */

kotlin {
    explicitApi()
    jvmToolchain(libs.versions.javaToolchain.get().toInt())
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(projects.core.music)
    api(libs.kotlin.test)
}
