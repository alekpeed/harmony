import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Copies the authored content pack into the app's assets.
 *
 * `content/` is where a curriculum author works (21_CONTENT_AUTHORING_GUIDE.md), and the app
 * reads assets. Rather than keeping two copies in step by hand, the build carries one across —
 * so editing a gate is editing one file, and there is no second copy to forget.
 *
 * The flattening is deliberate: authors get `content/curriculum/` and `content/exercises/`
 * because a gate and an exercise policy are different concerns (§2), while the app only needs
 * two files and should not have to know how the author's directory is arranged.
 */
abstract class SyncContentPack : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val generatedAssetDirectory: DirectoryProperty

    @TaskAction
    fun sync() {
        val source = contentDirectory.get().asFile
        val target = generatedAssetDirectory.get().asFile.resolve("content")
        target.deleteRecursively()
        target.mkdirs()

        FILES.forEach { (from, to) ->
            val file = source.resolve(from)
            if (!file.isFile) {
                throw GradleException(
                    "Missing content file: content/$from. The app cannot start without a " +
                        "curriculum, so this is a build failure rather than an empty campaign.",
                )
            }
            file.copyTo(target.resolve(to), overwrite = true)
            logger.lifecycle("Content: content/$from -> assets/content/$to")
        }
    }

    private companion object {
        /** Authored path to asset name. */
        val FILES = listOf(
            "curriculum/curriculum.json" to "curriculum.json",
            "exercises/exercise_policies.json" to "exercise_policies.json",
        )
    }
}
