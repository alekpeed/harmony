import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing, from somewhere that is not the repository.
 *
 * 17_BUILD_CI_INSTALL_RELEASE.md §3: "Release signing secrets belong in GitHub encrypted secrets
 * or a secure local signing setup, never committed." So the keystore is found either through
 * `keystore.properties` — which is gitignored — or through environment variables, which is how
 * CI supplies it. When neither is present the release build is simply unsigned rather than
 * failing, so `assembleRelease` still verifies that the shrinker and the resource stripper are
 * happy on a machine that has no secrets.
 */
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

fun signingValue(key: String, environmentName: String): String? =
    keystoreProperties?.getProperty(key) ?: System.getenv(environmentName)

val releaseKeystorePath: String? = signingValue("storeFile", "HARMONY_KEYSTORE_FILE")
val releaseKeystorePassword: String? = signingValue("storePassword", "HARMONY_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = signingValue("keyAlias", "HARMONY_KEY_ALIAS")
val releaseKeyPassword: String? = signingValue("keyPassword", "HARMONY_KEY_PASSWORD")
/**
 * The content pack's own version and schema, read out of the authored curriculum.
 *
 * 17 §6 keeps three numbers apart: the app version, the content version, and the content schema.
 * Reading the latter two from the file that carries them means they cannot drift from the
 * content actually shipped — which is the whole reason they are separate numbers.
 */
val curriculumFile = rootProject.file("content/curriculum/curriculum.json")

fun curriculumField(name: String): String =
    Regex("\"$name\"\\s*:\\s*\"?([^\",}]+)\"?")
        .find(curriculumFile.readText())
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?: error("content/curriculum/curriculum.json has no $name")

val harmonyContentVersion: String = curriculumField("contentVersion")
val harmonyContentSchema: Int = curriculumField("schemaVersion").toInt()

/**
 * A build identity that changes when the build changes.
 *
 * Every debug build used to be `0.1.0`, `versionCode 1`, in a file called `app-debug.apk`. Three
 * different builds reached a tablet under that one name, and there was no way — on the device or
 * off it — to tell which one was installed. Two of them differed by a few hundred bytes inside a
 * 19 MB package, so even the file size was not a clue. That is not a cosmetic problem: it makes
 * "did my fix reach the device" unanswerable, and every question after it a guess.
 *
 * So the commit count is the version code and the short SHA is in the version name, the APK is
 * named after both, and the app prints them on screen in a debug build. A build can now be
 * identified from across the room.
 *
 * `providers.exec` rather than a direct process call: it is what keeps the configuration cache
 * usable, and it re-runs when the repository moves.
 */
fun gitOutput(command: List<String>, fallback: String): String {
    val output = runCatching {
        providers.exec { commandLine(command) }.standardOutput.asText.get().trim()
    }.getOrNull()
    return if (output.isNullOrEmpty()) fallback else output
}

val gitCommitCount: Int =
    gitOutput(listOf("git", "rev-list", "--count", "HEAD"), fallback = "1").toIntOrNull() ?: 1
val gitShortSha: String = gitOutput(listOf("git", "rev-parse", "--short", "HEAD"), fallback = "nogit")
val gitDirty: Boolean = gitOutput(listOf("git", "status", "--porcelain"), fallback = "").isNotEmpty()
val buildIdentity: String = gitShortSha + if (gitDirty) "+dirty" else ""
val harmonyVersionName: String = "0.1.0.$gitCommitCount-$buildIdentity"

val canSignRelease: Boolean = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() } && file(releaseKeystorePath!!).isFile


android {
    namespace = "com.harmonygates"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.harmonygates"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // One per commit, so the installer can always tell an older build from a newer one.
        versionCode = gitCommitCount

        // 17 §6: a semantic app version, and a content version that moves independently of it.
        // Both are readable at runtime so a bug report can say which of the two it came from.
        versionName = harmonyVersionName
        buildConfigField("String", "CONTENT_VERSION", "\"$harmonyContentVersion\"")
        buildConfigField("int", "CONTENT_SCHEMA", harmonyContentSchema.toString())
        buildConfigField("String", "BUILD_IDENTITY", "\"$buildIdentity\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
            description = "Copies the clean Home plate and its interaction map from interface/."
            interfaceDirectory.set(rootProject.layout.projectDirectory.dir("interface"))
            // The clean static plate, not the approved JPG. `interface/maps/home.json` marks that
            // JPG `legacy-reference-only` because it has runtime values baked into it — a
            // profile name, an XP figure, a streak, a clock. Those have to be drawn from state
            // above the plate, and a background that already contains yesterday's numbers makes
            // that impossible to do correctly.
            sourceFileName.set("assets/home-clean-background.png")
            mapFileName.set("maps/home.json")
            resourceName.set("home_approved")
            mapResourceName.set("home_interaction_map")
            // Matches HarmonyColorTokens.background, so a pending artwork is never a bright flash.
            placeholderColor.set("#12131A")
            artworkRequired.set(true)
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            syncArtwork,
            SyncInterfaceArtwork::generatedResourceDirectory,
        )

        // The Progression Run plate: the clean room the track runs through, with no baked
        // orbs, pedestals or path in it. Supplied under `interface/assets/`, which is where
        // `interface/README.md` now places screen artwork.
        val syncProgressionRun = tasks.register<SyncInterfaceArtwork>(
            "sync${variant.name.replaceFirstChar(Char::uppercase)}ProgressionRunArtwork",
        ) {
            group = "build"
            description = "Copies the Progression Run plate and track map from interface/."
            interfaceDirectory.set(rootProject.layout.projectDirectory.dir("interface"))
            sourceFileName.set("assets/progression-run-background.png")
            mapFileName.set("maps/progression-run.json")
            resourceName.set("progression_run_background")
            mapResourceName.set("progression_run_map")
            placeholderColor.set("#12131A")
            artworkRequired.set(true)
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
            description = "Copies the curriculum, policies and progressions from content/ into assets."
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

/**
 * A copy of each APK named after the build that produced it.
 *
 * AGP always writes `app-debug.apk`, whatever the version is. Three files under one name is how
 * a fix that never reached the device looks identical to a fix that did, so every build also
 * lands under a name that says which build it is.
 */
androidComponents {
    onVariants { variant ->
        // Both pulled out of the variant here: capturing the variant itself inside the task's
        // rename lambda drags the whole AGP object graph into the configuration cache with it.
        val variantName = variant.name
        val taskName = variantName.replaceFirstChar(Char::uppercase)
        val targetName = "harmony-gates-$harmonyVersionName-$variantName.apk"

        val nameApk = tasks.register<Copy>("nameHarmony${taskName}Apk") {
            group = "build"
            description = "Copies the $variantName APK to a filename carrying its version and commit."
            from(layout.buildDirectory.dir("outputs/apk/$variantName")) {
                include("*.apk")
                // A previous run's named copy must not be picked up and renamed onto itself.
                exclude("harmony-gates-*.apk")
                rename { targetName }
            }
            // Its own directory, not AGP's: writing a second file into `outputs/apk` makes
            // Gradle's own listing task read a directory another task is still writing to.
            into(layout.buildDirectory.dir("outputs/harmony"))
        }
        tasks.matching { it.name == "assemble$taskName" }.configureEach { finalizedBy(nameApk) }
    }
}
