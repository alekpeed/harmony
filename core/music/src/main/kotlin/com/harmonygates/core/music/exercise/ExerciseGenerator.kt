package com.harmonygates.core.music.exercise

import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.harmony.AlteredDominants
import com.harmonygates.core.music.parse.StandardRoots
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.random.RandomSource
import com.harmonygates.core.music.random.SeededRandomFactory
import com.harmonygates.core.music.random.DefaultSeededRandomFactory
import com.harmonygates.core.music.realize.ChordRealizer
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.BassRequirement
import com.harmonygates.core.music.voicing.Inversion
import com.harmonygates.core.music.voicing.VoicingFamilies
import com.harmonygates.core.music.voicing.VoicingPolicy
import com.harmonygates.core.music.voicing.VoicingRecipe

/**
 * One generated exercise.
 *
 * The contract from 20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §2. Carries its own [seed] so any
 * exercise a player saw can be reproduced exactly — which is what makes a bug report about a
 * specific chord actionable (10_ANDROID_ARCHITECTURE.md §12).
 */
public data class ExerciseInstance(
    val id: ExerciseInstanceId,
    val definitionId: ExerciseDefinitionId,
    val seed: Long,
    val skillIds: Set<SkillId>,
    val chord: ChordSpec,
    val requirement: ExerciseRequirement,
    val presentation: PresentationSpec,
    val inversion: Inversion,
    /** The tones of the chord, spelled, for the note-names assistance channel. */
    val spelledTones: List<SpelledPitchClass>,
    /** A playable rendering, for the keyboard-highlighting channel. Null when none fits. */
    val targetNotes: List<Int> = emptyList(),
)

/** What the generator needs to know beyond the policy itself. */
public data class GenerationContext(
    /** Roots already used in this session, so coverage can be spread (02 §9). */
    val rootsAlreadySeen: Set<SpelledPitchClass> = emptySet(),
    /** The previous exercise, so the same one is not asked twice in a row. */
    val previousChord: ChordSpec? = null,
    val index: Int = 0,
)

/** Builds exercises from a policy and a seed. */
public interface ExerciseGenerator {
    public fun generate(
        policy: ExercisePolicy,
        seed: Long,
        context: GenerationContext = GenerationContext(),
    ): ExerciseInstance
}

/**
 * The production generator.
 *
 * Deterministic by construction: everything random comes from a [RandomSource] built from the
 * seed, so the same seed and context always produce the same exercise. Non-negotiable design
 * rule 6 depends on that, and so does any future bug report that says "the Db chord was wrong".
 *
 * Weighted sampling follows 02_GAME_LOOP_AND_PROGRESSION.md §9, with the terms that exist yet:
 * coverage of untested roots, novelty, and avoidance of an immediate duplicate. The weakness
 * and spaced-review terms need recorded mastery, which arrives in Phase 5; the shape here has
 * room for them rather than needing to be rewritten around them.
 */
public class DefaultExerciseGenerator(
    private val realizer: ChordRealizer = DefaultChordRealizer(),
    private val randomFactory: SeededRandomFactory = DefaultSeededRandomFactory,
) : ExerciseGenerator {

    override fun generate(
        policy: ExercisePolicy,
        seed: Long,
        context: GenerationContext,
    ): ExerciseInstance {
        val random = randomFactory.create(seed)
        val roots = policy.rootPool.ifEmpty { StandardRoots }

        val root = chooseRoot(roots, random, context)
        val formulaId = random.pick(policy.formulaPool)
        val inversion = chooseInversion(policy, random)
        val chord = ChordSpec(root = root, formulaId = formulaId)

        // A chord that standard notation cannot write must never reach a player. Falling back to
        // the first writable root in the pool keeps generation total rather than throwing at the
        // player's expense; the Phase 6 content validator will reject such pools up front.
        val writable = if (realizer.trySpell(chord) is SpellingResult.Spelled) {
            chord
        } else {
            roots.firstOrNull { candidate ->
                realizer.trySpell(ChordSpec(candidate, formulaId)) is SpellingResult.Spelled
            }?.let { ChordSpec(it, formulaId) } ?: chord
        }

        // Region 10 builds an altered dominant by altering a plain one rather than by naming a
        // separate chord, so the pool is applied here to whatever root and quality came out
        // above. A pool set against a chord that is not a dominant leaves it alone: the
        // authored formula is what the gate is teaching, and inventing a `Cmaj7b9` to satisfy
        // the pool would be a different lesson.
        val altered = if (policy.alterationPool.isEmpty()) {
            writable
        } else {
            val alterations = random.pick(policy.alterationPool)
            AlteredDominants.alter(writable, *alterations.toTypedArray()) ?: writable
        }

        // A named family is resolved here rather than in the policy, because the tones a shape
        // needs depend on the chord it is applied to: rootless A on a dominant sounds the
        // thirteenth, on a minor seventh the fifth. The recipe may also extend the chord with
        // the tensions the shape supplies, which is why it can replace `altered`.
        val recipe = policy.voicingFamily?.let { VoicingFamilies.recipe(it, altered) }
        val judged = recipe?.chord ?: altered

        val spelled = realizer.chordTones(judged)
        val voicingPolicy = voicingPolicyFor(policy, judged, inversion, recipe)
        val targetVoicing = realizer.generateVoicings(judged, voicingPolicy).firstOrNull()

        return ExerciseInstance(
            id = ExerciseInstanceId("ex-$seed-${context.index}"),
            definitionId = ExerciseDefinitionId(policy.id.value),
            seed = seed,
            skillIds = policy.skillIds,
            chord = judged,
            requirement = requirementFor(policy, judged, spelled, voicingPolicy, targetVoicing),
            presentation = policy.presentation,
            inversion = inversion,
            spelledTones = spelled,
            targetNotes = targetVoicing?.pitches?.map { it.value }.orEmpty(),
        )
    }

    /**
     * Picks a root, preferring ones this session has not used.
     *
     * 02_GAME_LOOP_AND_PROGRESSION.md §9's coverage term. A session that asked for C six times
     * would teach one root well and eleven not at all, and gate completion explicitly requires
     * root coverage.
     */
    private fun chooseRoot(
        roots: List<SpelledPitchClass>,
        random: RandomSource,
        context: GenerationContext,
    ): SpelledPitchClass {
        val unseen = roots.filterNot { it in context.rootsAlreadySeen }
        val pool = unseen.ifEmpty { roots }
        // Avoid asking for the same chord twice running, unless there is nothing else to ask.
        val withoutRepeat = pool.filterNot { it == context.previousChord?.root }
        return random.pick(withoutRepeat.ifEmpty { pool })
    }

    private fun chooseInversion(policy: ExercisePolicy, random: RandomSource): Inversion =
        random.pick(policy.inversionPool)

    /**
     * Turns the inversion the exercise asked for into a bass requirement.
     *
     * Extended chords collapse to [Inversion.OTHER], which asks for nothing in particular —
     * 04_HARMONY_DOMAIN_ENGINE.md §7 declines to name inversions above the seventh, so an
     * exercise cannot demand one.
     */
    private fun voicingPolicyFor(
        policy: ExercisePolicy,
        chord: ChordSpec,
        inversion: Inversion,
        recipe: VoicingRecipe?,
    ): VoicingPolicy {
        // A family already says what must be lowest — rootless A puts the third there — so its
        // own bass requirement wins over the inversion pool, which is asking a different
        // question and would otherwise silently override the shape.
        if (recipe != null) return recipe.policy.copy(pitchRange = policy.pitchRange)

        val base = policy.voicingPolicy ?: VoicingPolicy(
            requiredDegrees = chord.requiredDegrees,
            optionalDegrees = chord.optionalDegrees,
        )
        val bass = when (inversion) {
            Inversion.ROOT -> BassRequirement.RootInBass
            Inversion.FIRST -> degreeBass(chord, 3)
            Inversion.SECOND -> degreeBass(chord, 5)
            Inversion.THIRD -> degreeBass(chord, 7)
            Inversion.OTHER -> BassRequirement.Unconstrained
        }
        return base.copy(bassRequirement = bass, pitchRange = policy.pitchRange)
    }

    private fun degreeBass(chord: ChordSpec, number: Int): BassRequirement =
        chord.degrees.firstOrNull { it.number == number }
            ?.let { BassRequirement.DegreeInBass(it) }
            ?: BassRequirement.RootInBass

    private fun requirementFor(
        policy: ExercisePolicy,
        chord: ChordSpec,
        spelled: List<SpelledPitchClass>,
        voicingPolicy: VoicingPolicy,
        targetVoicing: com.harmonygates.core.music.voicing.Voicing?,
    ): ExerciseRequirement = when (policy.answerMode) {
        AnswerMode.PitchClasses -> ExerciseRequirement.PitchSet(
            pitchClasses = spelled.toSet(),
            chord = chord,
            // The bass only matters when the exercise is about inversion.
            requiredBass = requiredBassSpelling(chord, voicingPolicy),
        )

        AnswerMode.ChordPolicy -> ExerciseRequirement.ChordPolicyMatch(chord, voicingPolicy)

        AnswerMode.ExactVoicing -> targetVoicing
            ?.let { ExerciseRequirement.ExactVoicing(it) }
            // No voicing fits the register, so fall back to the question that can still be asked
            // rather than presenting an exercise with no correct answer.
            ?: ExerciseRequirement.PitchSet(pitchClasses = spelled.toSet(), chord = chord)
    }

    private fun requiredBassSpelling(chord: ChordSpec, policy: VoicingPolicy): SpelledPitchClass? =
        when (val bass = policy.bassRequirement) {
            is BassRequirement.DegreeInBass -> realizer.spelledDegrees(chord)[bass.degree]
            BassRequirement.RootInBass -> null // root position is the default reading, not a constraint
            is BassRequirement.PitchClassInBass -> bass.pitchClass
            else -> null
        }
}
