@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "harmony-gates"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Modules are declared only once their directories exist (see 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md).
// core:audio arrives with phase 8.
include(":app")
include(":core:music")
include(":core:midi")
include(":core:data")
include(":core:designsystem")
include(":core:testing")
