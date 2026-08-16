package com.harmonygates.core.music.eartraining

import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.key.Functions
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.random.DefaultSeededRandomFactory
import com.harmonygates.core.music.random.RandomSource
import com.harmonygates.core.music.random.SeededRandomFactory
import com.harmonygates.core.music.realize.ChordRealizer
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.core.music.voicing.VoicingPolicy

/** How a stimulus should be rendered. Chosen by the gate, not by the generator. */
public data class StimulusSettings(
    /**
     * Instruments the stimulus may be played on.
     *
     * 07 §4 asks for a small approved set so "the player learns harmony rather than one spectral
     * fingerprint", and warns in the same breath against randomising so hard that timbre becomes
     * the difficulty. A short list is the whole mechanism.
     */
    val instrumentIds: List<String> = listOf(DEFAULT_INSTRUMENT),
    val tempoBpm: Int = DEFAULT_TEMPO,
    val velocity: Int = DEFAULT_VELOCITY,
    /** Milliseconds between the two chords of a comparison task. */
    val gapMillis: Long = DEFAULT_GAP_MILLIS,
    val replayRule: ReplayRule = ReplayRule.Unlimited,
) {
    public companion object {
        public const val DEFAULT_INSTRUMENT: String = "instrument.practice_tone"
        public const val DEFAULT_TEMPO: Int = 90
        public const val DEFAULT_VELOCITY: Int = 84
        public const val DEFAULT_GAP_MILLIS: Long = 900
    }
}

/** Builds ear exercises. */
public interface EarExerciseGenerator {
    public fun generate(
        family: EarTaskFamily,
        policy: ExercisePolicy,
        seed: Long,
        settings: StimulusSettings = StimulusSettings(),
        key: KeyContext? = null,
    ): EarExercise?
}

/**
 * The production ear generator.
 *
 * Every task family here builds its answer as an ordinary [ExerciseRequirement] — the same type
 * a chord gate uses, judged by the same evaluator. That is 07 §1 taken literally, and it is what
 * stops ear training from growing a second, subtly different idea of what a `G7` is.
 *
 * Deterministic from the seed, all the way down to which instrument was chosen and which
 * velocities were used, because §3 requires a reported error to be reproducible.
 */
public class DefaultEarExerciseGenerator(
    private val realizer: ChordRealizer = DefaultChordRealizer(),
    private val randomFactory: SeededRandomFactory = DefaultSeededRandomFactory,
) : EarExerciseGenerator {

    override fun generate(
        family: EarTaskFamily,
        policy: ExercisePolicy,
        seed: Long,
        settings: StimulusSettings,
        key: KeyContext?,
    ): EarExercise? {
        val random = randomFactory.create(seed)
        return when (family) {
            EarTaskFamily.REPRODUCE, EarTaskFamily.IDENTIFY_THEN_PLAY ->
                single(family, policy, seed, settings, random)

            EarTaskFamily.DIFFERENCE_DETECTION -> difference(policy, seed, settings, random)
            EarTaskFamily.FUNCTION_HEARING -> function(policy, seed, settings, random, key)
            EarTaskFamily.BASS_HEARING, EarTaskFamily.VOICE_LEADING_HEARING ->
                // Both need a moving line rather than a chord, and the sight-reading score
                // domain of Phase 9 is where a line is modelled. Returning null is honest;
                // inventing a half-version would be worse than saying not yet.
                null
        }
    }

    /** One chord, played once. The player reproduces it. */
    private fun single(
        family: EarTaskFamily,
        policy: ExercisePolicy,
        seed: Long,
        settings: StimulusSettings,
        random: RandomSource,
    ): EarExercise? {
        val chord = chordFrom(policy, random) ?: return null
        val voicing = voicingFor(chord, policy) ?: return null
        val instrument = random.pick(settings.instrumentIds)

        return EarExercise(
            stimulus = StimulusSpec(
                seed = seed,
                family = family,
                chords = listOf(chord),
                events = listOf(eventFor(voicing, 0, settings)),
                instrumentId = instrument,
                tempoBpm = settings.tempoBpm,
            ),
            requirement = requirementFor(chord),
            replayRule = settings.replayRule,
            instruction = if (family == EarTaskFamily.IDENTIFY_THEN_PLAY) {
                "Name what you hear, then play it."
            } else {
                "Play what you hear."
            },
        )
    }

    /**
     * Two chords a step apart in one dimension.
     *
     * §2 lists the changes worth hearing — major seventh against dominant, natural nine against
     * flat nine, root position against an inversion — and they are all *one* difference. Two
     * chords that differ in three ways teach nothing, because the player cannot tell which
     * difference they noticed.
     */
    private fun difference(
        policy: ExercisePolicy,
        seed: Long,
        settings: StimulusSettings,
        random: RandomSource,
    ): EarExercise? {
        val chord = chordFrom(policy, random) ?: return null
        val first = voicingFor(chord, policy) ?: return null

        val inverted = realizer.generateVoicings(chord, policyFor(policy))
            .firstOrNull { it.metadata.bassDegree != first.metadata.bassDegree }
            ?: return null

        return EarExercise(
            stimulus = StimulusSpec(
                seed = seed,
                family = EarTaskFamily.DIFFERENCE_DETECTION,
                chords = listOf(chord, chord),
                events = listOf(
                    eventFor(first, 0, settings),
                    eventFor(inverted, settings.gapMillis, settings),
                ),
                instrumentId = random.pick(settings.instrumentIds),
                tempoBpm = settings.tempoBpm,
            ),
            // The answer is the second chord, played. Naming the difference is the question;
            // reproducing it is the proof, which is §2's point about identify-then-play.
            requirement = ExerciseRequirement.ExactVoicing(inverted, octaveEquivalent = true),
            replayRule = settings.replayRule,
            instruction = "Two chords. Play the second one.",
            differenceDescription = "The bass moved: " +
                "${first.spelledPitches.first().pitchClass} became ${inverted.spelledPitches.first().pitchClass}.",
        )
    }

    /** A function in a key, heard rather than read. */
    private fun function(
        policy: ExercisePolicy,
        seed: Long,
        settings: StimulusSettings,
        random: RandomSource,
        key: KeyContext?,
    ): EarExercise? {
        val context = key ?: return null
        val function = random.pick(Functions.MajorTwoFiveOne)
        val chord = when (val resolved = function.resolveIn(context)) {
            is SpellingResult.Spelled -> resolved.value
            is SpellingResult.Overflow -> return null
        }
        val voicing = voicingFor(chord, policy) ?: return null

        return EarExercise(
            stimulus = StimulusSpec(
                seed = seed,
                family = EarTaskFamily.FUNCTION_HEARING,
                chords = listOf(chord),
                events = listOf(eventFor(voicing, 0, settings)),
                instrumentId = random.pick(settings.instrumentIds),
                tempoBpm = settings.tempoBpm,
                key = context,
            ),
            requirement = requirementFor(chord),
            replayRule = settings.replayRule,
            instruction = "In ${context.tonic}: play the chord you hear.",
        )
    }

    private fun chordFrom(policy: ExercisePolicy, random: RandomSource): ChordSpec? {
        if (policy.formulaPool.isEmpty()) return null
        val roots = policy.rootPool.ifEmpty { return null }
        val chord = ChordSpec(random.pick(roots), random.pick(policy.formulaPool))
        // A chord that cannot be written cannot be spelled back to the player either.
        return chord.takeIf { realizer.trySpell(it) is SpellingResult.Spelled }
    }

    private fun voicingFor(chord: ChordSpec, policy: ExercisePolicy): Voicing? =
        realizer.generateVoicings(chord, policyFor(policy)).firstOrNull()

    private fun policyFor(policy: ExercisePolicy): VoicingPolicy =
        policy.voicingPolicy ?: VoicingPolicy(
            requiredDegrees = emptySet(),
            pitchRange = policy.pitchRange,
        )

    private fun requirementFor(chord: ChordSpec) = ExerciseRequirement.PitchSet(
        pitchClasses = realizer.chordTones(chord).toSet(),
        chord = chord,
    )

    /**
     * One chord as a playable event.
     *
     * Velocities are flat and the onset is exact. §8 is explicit that humanisation is for
     * demonstration audio only and must "never humanize timed reference clicks or anything used
     * as a timing ground truth" — and a stimulus the player is about to be judged against is
     * exactly that.
     */
    private fun eventFor(voicing: Voicing, atMillis: Long, settings: StimulusSettings) = StimulusEvent(
        voicing = voicing,
        atMillis = atMillis,
        velocities = List(voicing.voiceCount) { settings.velocity },
    )
}
