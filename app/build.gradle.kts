plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}


android {
    namespace = "com.harmonygates"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.harmonygates"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Debug exposes MIDI diagnostics and seeded exercise controls
            // (17_BUILD_CI_INSTALL_RELEASE.md §1).
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        lintConfig = rootProject.file("config/lint/lint.xml")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(libs.versions.javaToolchain.get().toInt())
}

dependencies {
    implementation(projects.core.music)
    implementation(projects.core.midi)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

/**
 * Pulls the approved artwork out of `interface/` into generated resources.
 *
 * `interface/` stays the single source of truth (see docs/INTERFACE_INTEGRATION.md): committing
 * a new export there and rebuilding is the whole workflow, with no second copy to keep in step.
 *
 * Registered per variant through the variant API, which is how AGP 9 wires a generated
 * directory together with its task dependency.
 */
androidComponents {
    onVariants { variant ->
        val syncArtwork = tasks.register<SyncInterfaceArtwork>(
            "sync${variant.name.replaceFirstChar(Char::uppercase)}InterfaceArtwork",
        ) {
            group = "build"
            description = "Copies the approved home artwork from interface/ into generated resources."
            interfaceDirectory.set(rootProject.layout.projectDirectory.dir("interface"))
            sourceFileName.set("harmony_home_approved.jpg")
            mapFileName.set("maps/home.json")
            resourceName.set("home_approved")
            mapResourceName.set("home_interaction_map")
            // Matches HarmonyColorTokens.background, so a pending artwork is never a bright flash.
            placeholderColor.set("#12131A")
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            syncArtwork,
            SyncInterfaceArtwork::generatedResourceDirectory,
        )

        // The Progression Run plate. No approved background has been supplied yet, so this
        // generates a placeholder and the screen draws its track over the theme background —
        // which is exactly the layering the handoff requires, with or without the plate. When
        // the clean room export lands in `interface/`, it drops in with no code change.
        val syncProgressionRun = tasks.register<SyncInterfaceArtwork>(
            "sync${variant.name.replaceFirstChar(Char::uppercase)}ProgressionRunArtwork",
        ) {
            group = "build"
            description = "Copies the Progression Run plate and track map from interface/."
            interfaceDirectory.set(rootProject.layout.projectDirectory.dir("interface"))
            sourceFileName.set("progression_run_background.jpg")
            mapFileName.set("maps/progression-run.json")
            resourceName.set("progression_run_background")
            mapResourceName.set("progression_run_map")
            placeholderColor.set("#12131A")
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            syncProgressionRun,
            SyncInterfaceArtwork::generatedResourceDirectory,
        )

        // The authored curriculum, carried into assets so `content/` stays the single copy.
        val syncContent = tasks.register<SyncContentPack>(
            "sync${variant.name.replaceFirstChar(Char::uppercase)}ContentPack",
        ) {
            group = "build"
            description = "Copies the curriculum and exercise policies from content/ into assets."
            contentDirectory.set(rootProject.layout.projectDirectory.dir("content"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncContent,
            SyncContentPack::generatedAssetDirectory,
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
