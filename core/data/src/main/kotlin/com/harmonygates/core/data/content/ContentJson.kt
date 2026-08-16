package com.harmonygates.core.data.content

import com.harmonygates.core.music.campaign.CompletionRule
import com.harmonygates.core.music.campaign.Curriculum
import com.harmonygates.core.music.campaign.CurriculumRegion
import com.harmonygates.core.music.campaign.GateDefinition
import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.core.music.campaign.RegionId
import com.harmonygates.core.music.assistance.AssistanceLevel
import com.harmonygates.core.music.campaign.Unlock
import com.harmonygates.core.music.chord.ChordFormulaId
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.exercise.AnswerMode
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.PresentationSpec
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.harmony.DominantAlteration
import com.harmonygates.core.music.mastery.ErrorClass
import com.harmonygates.core.music.performance.OnsetPolicy
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.voicing.Inversion
import com.harmonygates.core.music.voicing.VoicingFamilies
import com.harmonygates.core.music.voicing.VoicingFamily
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk shape of authored content.
 *
 * Separate types from the domain ones on purpose. 21_CONTENT_AUTHORING_GUIDE.md §2 keeps gate
 * definitions, exercise policies and chord formulas as three concerns, and a serialisation
 * annotation on a domain class would quietly make the file format part of the domain's API —
 * so a rename in `core:music` could not happen without breaking every authored file.
 *
 * Every reference here is a string. Resolving them, and failing loudly when they do not resolve,
 * is [ContentDecoder]'s job.
 */
@Serializable
internal data class CurriculumJson(
    val schemaVersion: Int,
    val contentVersion: String,
    val regions: List<RegionJson>,
)

@Serializable
internal data class RegionJson(
    val id: String,
    val title: String,
    val summary: String? = null,
    val prerequisites: List<String> = emptyList(),
    val gates: List<GateJson>,
)

@Serializable
internal data class GateJson(
    val id: String,
    val title: String,
    val objective: String,
    val skills: List<String>,
    val prerequisites: List<String> = emptyList(),
    val exercisePolicyId: String,
    val completionRule: CompletionRuleJson = CompletionRuleJson(),
    val preview: String? = null,
    val rewards: List<UnlockJson> = emptyList(),
    val remediation: Map<String, String> = emptyMap(),
    val isChallenge: Boolean = false,
)

@Serializable
internal data class CompletionRuleJson(
    val minimumAttempts: Int = CompletionRule.DEFAULT_MINIMUM_ATTEMPTS,
    val recentWeightedAccuracy: Double = CompletionRule.DEFAULT_ACCURACY,
    val maximumCriticalErrors: Int = CompletionRule.DEFAULT_MAXIMUM_CRITICAL_ERRORS,
    val minimumRootCoverage: Int? = null,
    val maximumMedianResponseMillis: Long? = null,
)

@Serializable
internal data class UnlockJson(
    val kind: String,
    val value: String,
)

/** The exercise-policy file. 21 §4's checklist, as data. */
@Serializable
internal data class PolicyFileJson(
    val schemaVersion: Int,
    val contentVersion: String,
    val policies: List<PolicyJson>,
)

@Serializable
internal data class PolicyJson(
    val id: String,
    val skills: List<String>,
    @SerialName("roots") val rootPool: List<String> = emptyList(),
    @SerialName("formulas") val formulaPool: List<String>,
    @SerialName("inversions") val inversionPool: List<String> = listOf("ROOT"),
    @SerialName("alterations") val alterationPool: List<List<String>> = emptyList(),
    val answerMode: String = "PitchClasses",
    /**
     * An `A0`..`A7` level from 01_PRODUCT_AND_FUNCTIONAL_SCOPE.md §4.
     *
     * A shorthand for [presentation]: an author who writes `"assistanceLevel": "A1"` gets that
     * level's channels. Setting both is an authoring mistake and is refused rather than silently
     * resolved one way, because which one wins would be invisible in the file.
     */
    val assistanceLevel: String? = null,
    /** A named family from `VoicingFamily`, resolved per chord at generation time. */
    val voicingFamily: String? = null,
    val presentation: PresentationJson = PresentationJson(),
    val onsetPolicy: OnsetPolicyJson = OnsetPolicyJson(),
    val lowestNote: Int? = null,
    val highestNote: Int? = null,
    val sessionLength: Int = ExercisePolicy.DEFAULT_SESSION_LENGTH,
)

@Serializable
internal data class PresentationJson(
    val chordSymbol: Boolean = true,
    val noteNames: Boolean = false,
    val keyboardTargets: Boolean = false,
    val inversionLabel: Boolean = false,
    val romanNumeral: Boolean = false,
    val staffNotation: Boolean = false,
    val targetBassNote: Boolean = false,
    val voicingName: Boolean = false,
)

@Serializable
internal data class OnsetPolicyJson(
    val kind: String = "rolled",
    val maxSpreadMillis: Int = DEFAULT_ROLL_MILLIS,
) {
    companion object {
        const val DEFAULT_ROLL_MILLIS = 300
    }
}

/** A reference in a content file that does not resolve. */
public class ContentReferenceException(message: String) : IllegalArgumentException(message)

/**
 * Turns authored files into domain objects, refusing anything it cannot resolve.
 *
 * 21_CONTENT_AUTHORING_GUIDE.md §9 wants content validation to "fail on invalid references or
 * impossible content". Half of that happens here — a chord formula or inversion that does not
 * exist cannot become a policy at all — and the graph-shaped half happens in
 * `CurriculumValidator`, which needs the whole curriculum before it can say anything.
 */
internal object ContentDecoder {

    fun curriculum(json: CurriculumJson): Curriculum = Curriculum(
        schemaVersion = json.schemaVersion,
        contentVersion = json.contentVersion,
        regions = json.regions.map { region ->
            CurriculumRegion(
                id = RegionId(region.id),
                title = region.title,
                summary = region.summary,
                prerequisites = region.prerequisites.map { RegionId(it) }.toSet(),
                gates = region.gates.map(::gate),
            )
        },
    )

    private fun gate(json: GateJson) = GateDefinition(
        id = GateId(json.id),
        title = json.title,
        objective = json.objective,
        skillIds = json.skills.map { SkillId(it) }.toSet(),
        prerequisites = json.prerequisites.map { GateId(it) }.toSet(),
        exercisePolicyId = ExercisePolicyId(json.exercisePolicyId),
        completionRule = CompletionRule(
            minimumAttempts = json.completionRule.minimumAttempts,
            recentWeightedAccuracy = json.completionRule.recentWeightedAccuracy,
            maximumCriticalErrors = json.completionRule.maximumCriticalErrors,
            minimumRootCoverage = json.completionRule.minimumRootCoverage,
            maximumMedianResponseMillis = json.completionRule.maximumMedianResponseMillis,
        ),
        rewards = json.rewards.map(::unlock),
        preview = json.preview,
        remediation = json.remediation.mapKeys { (name, _) ->
            ErrorClass.entries.firstOrNull { it.name == name }
                ?: throw ContentReferenceException(
                    "Gate '${json.id}' maps remediation for '$name', which is not an error class",
                )
        }.mapValues { (_, policyId) -> ExercisePolicyId(policyId) },
        isChallenge = json.isChallenge,
    )

    private fun unlock(json: UnlockJson): Unlock = when (json.kind) {
        "region" -> Unlock.Region(RegionId(json.value))
        "voicing_family" -> Unlock.VoicingFamily(json.value)
        "practice_preset" -> Unlock.PracticePreset(ExercisePolicyId(json.value))
        "challenge_gate" -> Unlock.ChallengeGate(GateId(json.value))
        "instrument" -> Unlock.Instrument(json.value)
        else -> throw ContentReferenceException("'${json.kind}' is not a kind of unlock")
    }

    fun policies(json: PolicyFileJson): Map<ExercisePolicyId, ExercisePolicy> =
        json.policies.associate { policy -> ExercisePolicyId(policy.id) to policy(policy) }

    private fun policy(json: PolicyJson): ExercisePolicy {
        val formulas = json.formulaPool.map { name ->
            ChordFormulas.all.firstOrNull { it.id.value == name }?.id
                ?: ChordFormulaId(name).takeIf { id -> ChordFormulas.all.any { it.id == id } }
                ?: throw ContentReferenceException(
                    "Policy '${json.id}' uses the chord formula '$name', which does not exist",
                )
        }
        val inversions = json.inversionPool.map { name ->
            Inversion.entries.firstOrNull { it.name == name }
                ?: throw ContentReferenceException(
                    "Policy '${json.id}' asks for inversion '$name', which does not exist",
                )
        }
        // Authored as the symbols a chart uses — `[["b9"], ["#9", "b13"]]` — because that is how
        // an author thinks about them, and each inner list is one chord rather than one tone.
        val alterations = json.alterationPool.map { set ->
            set.map { symbol ->
                DominantAlteration.entries.firstOrNull { it.symbol == symbol }
                    ?: throw ContentReferenceException(
                        "Policy '${json.id}' asks for the alteration '$symbol', which is not one " +
                            "of ${DominantAlteration.entries.joinToString { it.symbol }}",
                    )
            }.toSet()
        }
        if (alterations.any { it.isEmpty() }) {
            throw ContentReferenceException(
                "Policy '${json.id}' authors an empty alteration set, which is a plain dominant",
            )
        }
        val answerMode = AnswerMode.entries.firstOrNull { it.name == json.answerMode }
            ?: throw ContentReferenceException(
                "Policy '${json.id}' uses the answer mode '${json.answerMode}', which does not exist",
            )
        val family = json.voicingFamily?.let { name ->
            VoicingFamily.entries.firstOrNull { it.name == name }
                ?: throw ContentReferenceException(
                    "Policy '${json.id}' names the voicing family '$name', which does not exist",
                )
        }
        if (family != null && family !in VoicingFamilies.supported) {
            throw ContentReferenceException(
                "Policy '${json.id}' names the voicing family '$family', which has no recipe yet",
            )
        }
        val roots = json.rootPool.map { name ->
            SpelledPitchClass.parseOrNull(name)
                ?: throw ContentReferenceException("Policy '${json.id}' uses '$name' as a root")
        }

        val level = json.assistanceLevel?.let { id ->
            AssistanceLevel.byId(id)
                ?: throw ContentReferenceException(
                    "Policy '${json.id}' asks for assistance level '$id', which does not exist",
                )
        }
        if (level != null && json.presentation != PresentationJson()) {
            throw ContentReferenceException(
                "Policy '${json.id}' sets both an assistance level and presentation switches; " +
                    "pick one, because which of them wins would not be visible in the file",
            )
        }

        return ExercisePolicy(
            id = ExercisePolicyId(json.id),
            skillIds = json.skills.map { SkillId(it) }.toSet(),
            rootPool = roots,
            formulaPool = formulas,
            inversionPool = inversions,
            alterationPool = alterations,
            answerMode = answerMode,
            // Resolved against the actual chord at generation time, because a rootless voicing
            // of Cmaj7 and of G7 need different tones. The policy records the intent only.
            voicingFamily = family,
            presentation = level?.profile?.presentation ?: PresentationSpec(
                showChordSymbol = json.presentation.chordSymbol,
                showSpelledNoteNames = json.presentation.noteNames,
                showKeyboardTargets = json.presentation.keyboardTargets,
                showInversionLabel = json.presentation.inversionLabel,
                showRomanNumeral = json.presentation.romanNumeral,
                showStaffNotation = json.presentation.staffNotation,
                showTargetBassNote = json.presentation.targetBassNote,
                showVoicingName = json.presentation.voicingName,
            ),
            onsetPolicy = onsetPolicy(json),
            pitchRange = (json.lowestNote ?: ExercisePolicy.DEFAULT_RANGE.first)..
                (json.highestNote ?: ExercisePolicy.DEFAULT_RANGE.last),
            sessionLength = json.sessionLength,
        )
    }

    private fun onsetPolicy(json: PolicyJson): OnsetPolicy = when (json.onsetPolicy.kind) {
        "simultaneous" -> OnsetPolicy.Simultaneous(json.onsetPolicy.maxSpreadMillis)
        "rolled" -> OnsetPolicy.RolledAllowed(json.onsetPolicy.maxSpreadMillis)
        "unrestricted" -> OnsetPolicy.Unrestricted
        else -> throw ContentReferenceException(
            "Policy '${json.id}' uses the onset policy '${json.onsetPolicy.kind}', which does not exist",
        )
    }
}
