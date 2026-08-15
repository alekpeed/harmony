import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.math.abs

/**
 * Build-time validation of the assets handed over in `interface/`.
 *
 * An asset arriving corrupt is not hypothetical: `interface/harmony-home-approved.jpg` was
 * first committed as 14,997 bytes carrying no JPEG markers at all. Nothing in a normal build
 * would have noticed — AAPT does not decode a file it only copies — and the failure would have
 * surfaced as a blank home screen on a tablet, a long way from its cause.
 *
 * So the build checks. A file named like an image must be one, and must declare a size.
 */
abstract class CheckInterfaceAssets : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val interfaceDirectory: DirectoryProperty

    /** Expected aspect ratio, from the native artwork size in `interface/README.md`. */
    @get:Input
    abstract val expectedAspectRatio: Property<Double>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val candidates = interfaceDirectory.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in ImageHeader.SUPPORTED_EXTENSIONS }
            .sortedBy { it.path }
            .toList()

        val broken = mutableListOf<Pair<File, String>>()
        val lines = mutableListOf("Interface asset check")

        if (candidates.isEmpty()) {
            lines += "  no image assets present yet"
        }

        for (file in candidates) {
            val info = ImageHeader.read(file)
            if (info == null) {
                val reason = "not a decodable image — no JPEG, PNG, WebP or GIF header found " +
                    "in ${file.length()} bytes"
                broken += file to reason
                lines += "  BROKEN ${file.name}: $reason"
                continue
            }
            if (info.width <= 0 || info.height <= 0) {
                val reason = "reports a zero dimension (${info.width}x${info.height})"
                broken += file to reason
                lines += "  BROKEN ${file.name}: $reason"
                continue
            }

            // An aspect mismatch is reported but not fatal: a background or an illustration is
            // under no obligation to match the home frame's proportions.
            val expected = expectedAspectRatio.get()
            val note = if (abs(info.aspectRatio - expected) > ASPECT_TOLERANCE) {
                "  (aspect %.3f differs from the %.3f in interface/README.md)"
                    .format(info.aspectRatio, expected)
            } else {
                ""
            }
            lines += "  OK     ${file.name}: ${info.format} ${info.width}x${info.height}$note"
        }

        val output = lines.joinToString("\n")
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(output + "\n")
        logger.lifecycle(output)

        if (broken.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Interface assets are not usable:")
                    broken.forEach { (file, reason) -> appendLine("  - interface/${file.name}: $reason") }
                    appendLine()
                    appendLine("Re-export and commit the file as binary. A common cause is an upload")
                    appendLine("that stored a placeholder or a partial transfer rather than the image.")
                    appendLine("Verify before committing:")
                    appendLine("  file interface/<name>              # must say 'JPEG image data' or similar")
                    appendLine("  od -An -tx1 -N2 interface/<name>   # a JPEG starts ff d8, a PNG 89 50")
                },
            )
        }
    }

    private companion object {
        const val ASPECT_TOLERANCE = 0.01
    }
}
