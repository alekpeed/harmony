import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Copies an approved screen out of `interface/` and into the app's resources.
 *
 * `interface/` stays the single source of truth. Committing a new export and its interaction
 * map there, then rebuilding, is the entire integration workflow — no second copy to keep in
 * step, and no chance of the app shipping last week's artwork because someone updated one of
 * the two and forgot the other.
 *
 * The drawable is generated whether or not a usable artwork exists, so `R.drawable` always
 * resolves and the app needs neither reflection nor conditional compilation. Which of the two
 * it got is published as a generated boolean the app reads at runtime.
 */
abstract class SyncInterfaceArtwork : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val interfaceDirectory: DirectoryProperty

    /** Artwork file name inside `interface/`. */
    @get:Input
    abstract val sourceFileName: Property<String>

    /** Interaction map path, relative to `interface/`. */
    @get:Input
    abstract val mapFileName: Property<String>

    /** Android resource name for the artwork, without extension. */
    @get:Input
    abstract val resourceName: Property<String>

    /** Android `raw` resource name for the interaction map, without extension. */
    @get:Input
    abstract val mapResourceName: Property<String>

    /** Placeholder fill, `#AARRGGBB`, used when no usable artwork is present. */
    @get:Input
    abstract val placeholderColor: Property<String>

    /**
     * Whether a missing artwork is a build failure.
     *
     * False while a screen is waiting for its export: a placeholder is generated and the screen
     * falls back. True once the asset has been supplied — because from then on, a missing file
     * means somebody renamed or moved it, and the failure that produces is a blank screen on a
     * tablet with nothing anywhere saying why. That is precisely the silent failure
     * `checkInterfaceAssets` exists to prevent, and it is worth preventing here too.
     */
    @get:Input
    abstract val artworkRequired: Property<Boolean>

    @get:OutputDirectory
    abstract val generatedResourceDirectory: DirectoryProperty

    @TaskAction
    fun sync() {
        val outputRoot = generatedResourceDirectory.get().asFile
        // A stale artwork left from a previous run would collide with a freshly generated
        // placeholder of the same name, so the directory is rebuilt each time.
        outputRoot.deleteRecursively()
        val drawableDir = outputRoot.resolve("drawable-nodpi").apply { mkdirs() }
        val valuesDir = outputRoot.resolve("values").apply { mkdirs() }
        val rawDir = outputRoot.resolve("raw").apply { mkdirs() }

        val interfaceRoot = interfaceDirectory.get().asFile
        val name = resourceName.get()
        val artwork = interfaceRoot.resolve(sourceFileName.get())
        val info = ImageHeader.read(artwork)

        val map = interfaceRoot.resolve(mapFileName.get())
        val mapPresent = map.isFile && map.length() > 0
        if (mapPresent) {
            map.copyTo(rawDir.resolve("${mapResourceName.get()}.json"), overwrite = true)
        } else {
            // An empty array still parses, so the app has one code path rather than two.
            rawDir.resolve("${mapResourceName.get()}.json").writeText(EMPTY_MAP)
        }

        if (info != null) {
            artwork.copyTo(drawableDir.resolve("$name.${artwork.extension.lowercase()}"), overwrite = true)
            writeValues(valuesDir, name, available = true, width = info.width, height = info.height)
            logger.lifecycle(
                "Approved artwork: ${artwork.name} (${info.format} ${info.width}x${info.height}) " +
                    "-> R.drawable.$name" + if (mapPresent) ", map -> R.raw.${mapResourceName.get()}" else "",
            )
        } else {
            val why = if (artwork.exists()) "is not a decodable image" else "is not present"
            if (artworkRequired.getOrElse(false)) {
                throw GradleException(
                    "interface/${sourceFileName.get()} $why, and $name has been marked as " +
                        "supplied. Either restore the file at that exact path or set " +
                        "artworkRequired to false. A silent fallback here ships a blank screen.",
                )
            }
            drawableDir.resolve("$name.xml").writeText(placeholderDrawable())
            writeValues(valuesDir, name, available = false, width = 0, height = 0)
            logger.lifecycle(
                "Approved artwork pending: interface/${sourceFileName.get()} $why; " +
                    "R.drawable.$name is a placeholder and the screen falls back.",
            )
        }
    }

    private fun writeValues(directory: File, name: String, available: Boolean, width: Int, height: Int) {
        // Named after the resource rather than fixed, because more than one screen is synced now
        // and two generated `interface_artwork.xml` files would be needlessly confusing to read.
        directory.resolve("interface_artwork_$name.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Generated by syncInterfaceArtwork. Do not edit; edit interface/ instead. -->
            <resources>
                <bool name="${name}_available">$available</bool>
                <integer name="${name}_native_width">$width</integer>
                <integer name="${name}_native_height">$height</integer>
            </resources>

            """.trimIndent(),
        )
    }

    /**
     * A flat fill standing in for a missing artwork.
     *
     * Deliberately blank rather than an approximation of the approved design: the home screen
     * falls back to its action list when the artwork is unavailable, so this is only ever a
     * safe target for `R.drawable` to point at.
     */
    private fun placeholderDrawable(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <!-- Generated placeholder. No approved artwork was available at build time. -->
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="24dp"
            android:height="24dp"
            android:viewportWidth="24"
            android:viewportHeight="24">
            <path
                android:fillColor="${placeholderColor.get()}"
                android:pathData="M0,0 h24 v24 h-24 z" />
        </vector>

        """.trimIndent()

    private companion object {
        val EMPTY_MAP = """{"schemaVersion":1,"screen":"home","designSize":{"width":0,"height":0},"regions":[]}"""
    }
}
