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
 * Copies the approved Voice Leading environment plate and its control primitives into resources.
 *
 * The pack ships each control twice: as an SVG under its category folder, and as a raster of the
 * same thing under `png_exports/`. Android cannot load an SVG at runtime, and its own README says
 * the exports exist "when SVG rasterization is available", so the rasters are what ship and the
 * vectors stay in `interface/` as the editable source.
 *
 * `10_templates` and `11_guides` are deliberately excluded: they are layout wireframes and a
 * design grid, drawn for a person reading the pack, never for the app.
 */
abstract class SyncVoiceLeadingAssets : DefaultTask() {

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
            throw GradleException("Voice Leading assets are missing at ${assetRoot.path}")
        }

        val background = assetRoot.resolve(BACKGROUND_PATH)
        if (!background.isFile) {
            throw GradleException("Voice Leading is missing its environment plate at ${background.path}")
        }
        background.copyTo(drawableDir.resolve("$BACKGROUND_RESOURCE.png"), overwrite = true)

        val exports = assetRoot.resolve(EXPORTS_DIRECTORY)
        if (!exports.isDirectory) {
            throw GradleException("Voice Leading primitives are missing at ${exports.path}")
        }

        val copied = mutableMapOf<String, File>()
        exports.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            .filterNot { it.nameWithoutExtension in EXCLUDED }
            .forEach { file ->
                val resource = resourceNameFor(file)
                val clash = copied.put(resource, file)
                if (clash != null) {
                    throw GradleException(
                        "Two Voice Leading assets both map to R.drawable.$resource: " +
                            "${clash.path} and ${file.path}",
                    )
                }
                file.copyTo(drawableDir.resolve("$resource.png"), overwrite = true)
            }

        val map = interfaceRoot.resolve(MAP_PATH)
        if (!map.isFile || map.length() == 0L) {
            throw GradleException("Voice Leading screen map is missing at ${map.path}")
        }
        map.copyTo(rawDir.resolve("$MAP_RESOURCE.json"), overwrite = true)

        logger.lifecycle("Voice Leading: ${copied.size + 1} drawables and the screen map synced from interface/.")
    }

    /** `button-primary-default.png` becomes `vl_button_primary_default`. */
    private fun resourceNameFor(file: File): String =
        RESOURCE_PREFIX + file.nameWithoutExtension
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')

    private companion object {
        const val ASSET_PATH = "assets/voice_leading"
        const val BACKGROUND_PATH = "background/voice-leading-background-approved.png"
        const val BACKGROUND_RESOURCE = "voice_leading_background"
        const val EXPORTS_DIRECTORY = "png_exports"
        const val MAP_PATH = "maps/voice-leading.json"
        const val MAP_RESOURCE = "voice_leading_map"
        const val RESOURCE_PREFIX = "vl_"

        /** Wireframes and the design grid. For reading the pack, not for drawing the screen. */
        val EXCLUDED = setOf(
            "template-landing",
            "template-practice",
            "template-voice-path",
            "template-keyboard-practice",
            "1536x1024-grid-safe-zone",
        )
    }
}
