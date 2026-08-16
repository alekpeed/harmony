plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * `core:data` is where learning progress is kept.
 *
 * 11_DATA_MODEL_AND_PERSISTENCE.md §1 splits storage three ways and this module owns two of
 * them: Room for mutable learning data, DataStore for preferences. The third — bundled static
 * content — is read from assets here but authored in `content/`, so a curriculum edit is a
 * content change rather than a code change.
 *
 * Nothing in here decides whether an answer was correct or what a mastery estimate should be.
 * Those are `core:music`'s, and this module stores their conclusions.
 */

android {
    namespace = "com.harmonygates.core.data"
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

/**
 * The schema is exported and committed.
 *
 * 11 §4 requires that an app update "retain old attempt history" and "never silently
 * reinterpret old attempts". Neither is possible without knowing what the previous schema was,
 * so `core/data/schemas` is a source file that happens to be generated: a change to an entity
 * shows up as a diff there, and `RoomSchemaTest` fails if the version was not bumped with it.
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(projects.core.music)
    api(libs.kotlinx.coroutines.core)

    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.testing)
    // A pure-JVM SQLite. Room's own MigrationTestHelper needs a device; the schema Room exports
    // is ordinary DDL, and running it here proves the tables really can be created.
    testImplementation(libs.sqlite.jdbc)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
