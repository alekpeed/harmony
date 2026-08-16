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
import java.util.Base64

/**
 * Copies an approved screen out of `interface/` and into the app's resources.
 *
 * `interface/` stays the single source of truth. Committing a new export and its interaction
 * map there, then rebuilding, is the entire integration workflow — no second copy to keep in
 * step, and no chance of the app shipping last week's artwork because someone updated one of
 * the two and forgot the other.
 *
 * Binary artwork can also be stored as deterministic base64 parts when a repository client
 * cannot write raw binary safely. A source ending in `.b64` is reconstructed from either that
 * file or the lexically sorted files in `<source>.parts/`, then validated as an image before it
 * is exposed to Android. This prevents ASCII/base64 text from ever being packaged under an image
 * extension, which Android cannot decode at runtime.
 *
 * The drawable is generated whether or not a usable artwork exists, so `R.drawable` always
 * resolves and the app needs neither reflection nor conditional compilation. Which of the two
 * it got is published as a generated boolean the app reads at runtime.
 */
abstract class SyncInterfaceArtwork : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val interfaceDirectory: DirectoryProperty

    /** Artwork file name inside `interface/`. `.b64` sources are decoded before validation. */
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

    @get:Input
    abstract val artworkRequired: Property<Boolean>

    @get:OutputDirectory
    abstract val generatedResourceDirectory: DirectoryProperty

    @TaskAction
    fun sync() {
        val outputRoot = generatedResourceDirectory.get().asFile
        outputRoot.deleteRecursively()
        val drawableDir = outputRoot.resolve("drawable-nodpi").apply { mkdirs() }
        val valuesDir = outputRoot.resolve("values").apply { mkdirs() }
        val rawDir = outputRoot.resolve("raw").apply { mkdirs() }

        val interfaceRoot = interfaceDirectory.get().asFile
        val name = resourceName.get()
        val sourceName = sourceFileName.get()
        val artwork = materializeArtwork(interfaceRoot, sourceName, outputRoot)
        val info = artwork?.let(ImageHeader::read)

        val map = interfaceRoot.resolve(mapFileName.get())
        val mapPresent = map.isFile && map.length() > 0
        if (mapPresent) {
            map.copyTo(rawDir.resolve("${mapResourceName.get()}.json"), overwrite = true)
        } else {
            rawDir.resolve("${mapResourceName.get()}.json").writeText(EMPTY_MAP)
        }

        if (artwork != null && info != null) {
            artwork.copyTo(drawableDir.resolve("$name.${artwork.extension.lowercase()}"), overwrite = true)
            writeValues(valuesDir, name, available = true, width = info.width, height = info.height)
            logger.lifecycle(
                "Approved artwork: $sourceName (${info.format} ${info.width}x${info.height}) " +
                    "-> R.drawable.$name" + if (mapPresent) ", map -> R.raw.${mapResourceName.get()}" else "",
            )
        } else {
            val rawSource = interfaceRoot.resolve(sourceName)
            val parts = interfaceRoot.resolve("$sourceName.parts")
            val exists = rawSource.exists() || (parts.isDirectory && parts.listFiles()?.isNotEmpty() == true)
            val why = if (exists) "is not a decodable image" else "is not present"
            if (artworkRequired.getOrElse(false)) {
                throw GradleException(
                    "interface/$sourceName $why, and $name has been marked as supplied. " +
                        "Restore a valid image source. A silent fallback here ships a blank screen.",
                )
            }
            drawableDir.resolve("$name.xml").writeText(placeholderDrawable())
            writeValues(valuesDir, name, available = false, width = 0, height = 0)
            logger.lifecycle("Approved artwork pending: interface/$sourceName $why; R.drawable.$name is a placeholder.")
        }
    }

    private fun materializeArtwork(interfaceRoot: File, sourceName: String, outputRoot: File): File? {
        val source = interfaceRoot.resolve(sourceName)
        if (!sourceName.endsWith(".b64")) return source.takeIf(File::isFile)

        val encoded = when {
            source.isFile -> source.readText()
            else -> {
                val partsDirectory = interfaceRoot.resolve("$sourceName.parts")
                val parts = partsDirectory.listFiles()
                    ?.filter(File::isFile)
                    ?.sortedBy(File::getName)
                    .orEmpty()
                if (parts.isEmpty()) return null
                buildString(parts.sumOf { it.length().toInt() }) {
                    parts.forEach { append(it.readText().trim()) }
                }
            }
        }.filterNot(Char::isWhitespace)

        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (error: IllegalArgumentException) {
            throw GradleException("interface/$sourceName contains invalid base64 artwork data", error)
        }

        val decodedName = File(sourceName.removeSuffix(".b64")).name
        val decoded = outputRoot.resolve("decoded-source/$decodedName")
        decoded.parentFile.mkdirs()
        decoded.writeBytes(bytes)
        return decoded
    }

    private fun writeValues(directory: File, name: String, available: Boolean, width: Int, height: Int) {
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

    private fun placeholderDrawable(): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
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
