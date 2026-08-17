import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Copies the approved Ear Training asset tree out of `interface/` and into app resources.
 *
 * [SyncInterfaceArtwork] handles a screen that is one plate plus one map. Ear Training is a
 * layered console: several full-screen plates and around a hundred individual control assets that
 * are positioned, swapped and rotated at runtime. Rather than register that as ninety near
 * identical tasks, this walks the tree.
 *
 * `interface/` stays the single source of truth, exactly as it is for Home and Progression Run —
 * committing a new export there and rebuilding is still the whole integration workflow.
 *
 * Resource names are derived from the file name, prefixed `et_`, so a designer renaming a file
 * renames a resource and the build fails loudly rather than the screen quietly losing a control.
 */
abstract class SyncEarTrainingAssets : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val interfaceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedResourceDirectory: DirectoryProperty

    @TaskAction
    fun sync() {
        val outputRoot = generatedResourceDirectory.get().asFile
        outputRoot.deleteRecursively()
        val drawableDir = outputRoot.resolve("drawable-nodpi").apply { mkdirs() }
        val rawDir = outputRoot.resolve("raw").apply { mkdirs() }

        val interfaceRoot = interfaceDirectory.get().asFile
        val assetRoot = interfaceRoot.resolve(ASSET_PATH)
        if (!assetRoot.isDirectory) {
            throw GradleException("Ear Training assets are missing at ${assetRoot.path}")
        }

        // `source/` is the cutting sheets and reference comps. Copying them would put fifteen
        // megabytes of material the app must never render into the APK.
        val copied = mutableMapOf<String, File>()
        assetRoot.walkTopDown()
            .onEnter { it.name != SOURCE_DIRECTORY }
            .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            .forEach { file ->
                val resource = resourceNameFor(file)
                val clash = copied.put(resource, file)
                if (clash != null) {
                    throw GradleException(
                        "Two Ear Training assets both map to R.drawable.$resource: " +
                            "${clash.path} and ${file.path}",
                    )
                }
                file.copyTo(drawableDir.resolve("$resource.png"), overwrite = true)
            }

        REQUIRED_PLATES.forEach { plate ->
            if (plate !in copied) {
                throw GradleException("Ear Training is missing its $plate plate under ${assetRoot.path}")
            }
        }

        val map = interfaceRoot.resolve(MAP_PATH)
        if (!map.isFile || map.length() == 0L) {
            throw GradleException("Ear Training screen map is missing at ${map.path}")
        }
        map.copyTo(rawDir.resolve("$MAP_RESOURCE.json"), overwrite = true)

        logger.lifecycle("Ear Training: ${copied.size} drawables and the screen map synced from interface/.")
    }

    /**
     * `runtime/note_buttons/idle/note_Cs_idle.png` becomes `et_note_cs_idle`.
     *
     * Android resource names are lowercase, so the sharp/flat distinction has to survive the
     * fold: `note_Cs` and `note_C` differ by more than case, and `note_Cs` and `note_Db` are
     * different files, which is what lets the app pick a spelling rather than a pitch class.
     */
    private fun resourceNameFor(file: File): String =
        RESOURCE_PREFIX + file.nameWithoutExtension
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')

    private companion object {
        const val ASSET_PATH = "assets/ear_training"
        const val MAP_PATH = "maps/ear_training.json"
        const val MAP_RESOURCE = "ear_training_map"
        const val SOURCE_DIRECTORY = "source"
        const val RESOURCE_PREFIX = "et_"

        /** Without these the screen has nothing to draw, and a blank tablet says nothing about why. */
        val REQUIRED_PLATES = listOf(
            "et_ear_training_background_cinque_terre",
            "et_ear_training_setup_shell",
            "et_ear_training_section_layout",
            "et_ear_training_training_bar_shell",
        )
    }
}
