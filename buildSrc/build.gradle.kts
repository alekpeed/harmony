plugins {
    `kotlin-dsl`
}

/**
 * Build logic shared by every module.
 *
 * Currently one task: validation of the visual assets handed over in `interface/`. Convention
 * plugins for the Android modules will move here as `core:midi`, `core:audio` and `core:data`
 * arrive and the per-module boilerplate starts repeating.
 */

repositories {
    mavenCentral()
}
