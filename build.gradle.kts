plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}

/**
 * Aggregate task used by CI and by the phase-completion checklist in AGENTS.md.
 *
 * `./gradlew check` already runs tests + lint, but `verifyHarmony` states the intent
 * explicitly and gives later phases (content validation, screenshot diffing) a stable
 * place to attach to.
 */
tasks.register("verifyHarmony") {
    group = "verification"
    description = "Runs every unit-test and static-analysis task in the build."
    // `:core` is a container with no build file and therefore no `check` task; filtering on the
    // build file keeps this correct as `core:midi`, `core:audio` and `core:data` are added.
    dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:check" })
}
