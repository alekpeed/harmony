package com.harmonygates.core.data.content

import android.content.Context
import com.harmonygates.core.music.campaign.Curriculum
import com.harmonygates.core.music.campaign.CurriculumValidator
import com.harmonygates.core.music.campaign.GateDefinition
import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.core.music.campaign.ValidationResult
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.progression.ProgressionTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The authored campaign.
 *
 * 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §5's interface. Reads are suspending because the first
 * one touches the disk; everything after it is served from memory, since a curriculum is small
 * and does not change while the app is running.
 */
public interface ContentRepository {
    public suspend fun curriculum(): Curriculum

    public suspend fun gate(id: GateId): GateDefinition?

    public suspend fun exercisePolicy(id: ExercisePolicyId): ExercisePolicy?

    public suspend fun allPolicies(): Map<ExercisePolicyId, ExercisePolicy>

    /** The authored progressions, by id. Region 12's vocabulary is content, not code. */
    public suspend fun allProgressions(): Map<String, ProgressionTemplate>

    public suspend fun progression(id: String): ProgressionTemplate?

    /** What the validator makes of the loaded content. Surfaced so a diagnostics screen can show it. */
    public suspend fun validate(): ValidationResult
}

/** Where content bytes come from. Assets in the app, files on the build server. */
public fun interface ContentSource {
    public fun read(path: String): String
}

/**
 * Content read from the app's assets.
 *
 * The files are the ones in `content/`, copied in by the build, so the curriculum an author
 * edits and the curriculum the app runs are the same bytes.
 */
public class AssetContentSource(context: Context) : ContentSource {
    private val assets = context.applicationContext.assets

    override fun read(path: String): String = assets.open(path).use { it.readBytes().decodeToString() }
}

/**
 * Loads, decodes and validates the content pack.
 *
 * Validation happens on load rather than on demand, and a fatal problem throws. That is the
 * strong version of 21_CONTENT_AUTHORING_GUIDE.md §9 — the build already refuses to ship
 * unplayable content, and this makes sure a pack that somehow got through does not quietly
 * produce a campaign with a gate nobody can reach.
 */
public class DefaultContentRepository(
    private val source: ContentSource,
    private val curriculumPath: String = CURRICULUM_PATH,
    private val policyPath: String = POLICY_PATH,
    private val progressionPath: String = PROGRESSION_PATH,
) : ContentRepository {

    private val mutex = Mutex()
    private var loaded: LoadedContent? = null

    private suspend fun content(): LoadedContent = mutex.withLock {
        loaded ?: withContext(Dispatchers.IO) { load() }.also { loaded = it }
    }

    private fun load(): LoadedContent {
        val curriculum = ContentDecoder.curriculum(JSON.decodeFromString(source.read(curriculumPath)))
        val policies = ContentDecoder.policies(JSON.decodeFromString(source.read(policyPath)))
        val progressions =
            ContentDecoder.progressions(JSON.decodeFromString(source.read(progressionPath)))

        // A policy naming a progression that does not exist is a dangling reference like any
        // other, and it is checked here rather than in the validator because the validator's
        // job is the gate graph and it has never been handed the progressions.
        val missing = policies.values.mapNotNull { policy ->
            policy.progressionId?.takeIf { it !in progressions }?.let { "${policy.id} -> $it" }
        }
        if (missing.isNotEmpty()) {
            throw ContentReferenceException(
                "These policies name a progression that is not authored: ${missing.joinToString()}",
            )
        }

        val validation = CurriculumValidator(policies.keys).validate(curriculum)
        check(validation.isValid) {
            "The bundled curriculum is unplayable:\n${validation.report()}"
        }
        return LoadedContent(curriculum, policies, progressions, validation)
    }

    override suspend fun curriculum(): Curriculum = content().curriculum

    override suspend fun gate(id: GateId): GateDefinition? = content().curriculum.gate(id)

    override suspend fun exercisePolicy(id: ExercisePolicyId): ExercisePolicy? = content().policies[id]

    override suspend fun allPolicies(): Map<ExercisePolicyId, ExercisePolicy> = content().policies

    override suspend fun allProgressions(): Map<String, ProgressionTemplate> = content().progressions

    override suspend fun progression(id: String): ProgressionTemplate? = content().progressions[id]

    override suspend fun validate(): ValidationResult = content().validation

    private data class LoadedContent(
        val curriculum: Curriculum,
        val policies: Map<ExercisePolicyId, ExercisePolicy>,
        val progressions: Map<String, ProgressionTemplate>,
        val validation: ValidationResult,
    )

    public companion object {
        public const val CURRICULUM_PATH: String = "content/curriculum.json"
        public const val POLICY_PATH: String = "content/exercise_policies.json"
        public const val PROGRESSION_PATH: String = "content/progressions.json"

        internal val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
