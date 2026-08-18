package com.harmonygates.core.music.eartraining

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.key.Functions
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.random.DefaultSeededRandomFactory
import com.harmonygates.core.music.random.RandomSource
import com.harmonygates.core.music.random.SeededRandomFactory
import com.harmonygates.core.music.realize.ChordRealizer
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.core.music.voicing.VoicingPolicy
import kotlin.math.abs

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

    /**
     * A reference note, then one chord. The player reproduces the chord.
     *
     * The reference is the chord's own root, sounded alone first. Answering a bare, unannounced
     * chord cold is a perfect-pitch task; hearing the root immediately before it turns the same
     * exercise into a relative-pitch one — find the third, the fifth, the seventh, above a note
     * you were just given — which is the skill ear training actually teaches.
     */
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
        val anchor = anchorEvent(chord, ChordDegree.ROOT, policy, settings)
        val chordAt = if (anchor != null) ANCHOR_DURATION_MILLIS else 0L

        return EarExercise(
            stimulus = StimulusSpec(
                seed = seed,
                family = family,
                chords = listOf(chord),
                events = listOfNotNull(anchor, eventFor(voicing, chordAt, settings)),
                instrumentId = instrument,
                tempoBpm = settings.tempoBpm,
            ),
            requirement = requirementFor(chord),
            replayRule = settings.replayRule,
            instruction = if (family == EarTaskFamily.IDENTIFY_THEN_PLAY) {
                "A reference note, then a chord. Name it, then play it."
            } else {
                "A reference note, then a chord. Play what you hear."
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

        val inverted = preferredVoicing(chord, policy) { it.metadata.bassDegree != first.metadata.bassDegree }
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

        // The reference here is the key's tonic, not the target chord's root: a ii or a V means
        // nothing in isolation, only relative to home. Naming the key in the instruction is not
        // enough on its own — this is what actually lets a player hear "home" before judging
        // distance from it.
        val tonicChord = ChordSpec(context.tonic, ChordFormulas.MajorTriad.id)
        val anchor = anchorEvent(tonicChord, ChordDegree.ROOT, policy, settings)
        val chordAt = if (anchor != null) ANCHOR_DURATION_MILLIS else 0L

        return EarExercise(
            stimulus = StimulusSpec(
                seed = seed,
                family = EarTaskFamily.FUNCTION_HEARING,
                chords = listOf(chord),
                events = listOfNotNull(anchor, eventFor(voicing, chordAt, settings)),
                instrumentId = random.pick(settings.instrumentIds),
                tempoBpm = settings.tempoBpm,
                key = context,
            ),
            requirement = requirementFor(chord),
            replayRule = settings.replayRule,
            instruction = "In ${context.tonic}: the tonic, then the chord. Play what you hear.",
        )
    }

    private fun chordFrom(policy: ExercisePolicy, random: RandomSource): ChordSpec? {
        if (policy.formulaPool.isEmpty()) return null
        val roots = policy.rootPool.ifEmpty { return null }
        val chord = ChordSpec(random.pick(roots), random.pick(policy.formulaPool))
        // A chord that cannot be written cannot be spelled back to the player either.
        return chord.takeIf { realizer.trySpell(it) is SpellingResult.Spelled }
    }

    private fun voicingFor(chord: ChordSpec, policy: ExercisePolicy): Voicing? = preferredVoicing(chord, policy)

    /**
     * The voicing a player would actually want to hear: the tightest (closed) shape available,
     * placed in the octave closest to [COMFORTABLE_BASS_MIDI].
     *
     * `ChordRealizer.generateVoicings` enumerates a candidate in every octave the policy's pitch
     * range admits and orders them tightest-span-first, lowest-bass-second — a tie-break built
     * for chord-gate reading (lower is safer to sight-read), not for playback. Taken as-is here,
     * that tie-break always won at the *bottom* of a two-octave range: an ear-training stimulus
     * a full octave and more below middle C, which is both harder to sing back and, on a
     * synthesised tone, noticeably harsher than the same chord a register or two higher. This
     * re-ranks by distance from a comfortable target instead of by raw pitch.
     */
    private fun preferredVoicing(
        chord: ChordSpec,
        policy: ExercisePolicy,
        matching: (Voicing) -> Boolean = { true },
    ): Voicing? {
        val candidates = realizer.generateVoicings(chord, policyFor(policy)).filter(matching)
        if (candidates.isEmpty()) return null
        val tightest = candidates.minOf { it.metadata.spanSemitones }
        return candidates
            .filter { it.metadata.spanSemitones == tightest }
            .minByOrNull { abs(it.bass.value - COMFORTABLE_BASS_MIDI) }
    }

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
     * A single reference note, sounded alone before the chord it belongs to.
     *
     * Built from the same [ChordSpec] the exercise judges — or, for function hearing, a throwaway
     * tonic triad standing in for "home" — so the note is tagged with a real [ChordDegree] the
     * same way any other voicing is, rather than being a bare untyped pitch.
     *
     * Returns null only if [degree] cannot be spelled for [spec], which callers treat as "skip
     * the reference" rather than "fail the exercise": a stimulus with no anchor is still a
     * playable, if harder, exercise.
     */
    private fun anchorEvent(
        spec: ChordSpec,
        degree: ChordDegree,
        policy: ExercisePolicy,
        settings: StimulusSettings,
    ): StimulusEvent? {
        // `analyze` spells every degree of `spec`, not just [degree], and throws rather than
        // returning null on overflow. `chord` arrives pre-validated by `chordFrom`, but the
        // tonic triad `function()` builds for its anchor never has been — so this checks first,
        // the same way `chordFrom` does, rather than let an unspellable key crash the exercise.
        if (realizer.trySpell(spec) !is SpellingResult.Spelled) return null
        val spelling = realizer.spelledDegrees(spec)[degree] ?: return null
        val note = nearestNote(spelling, COMFORTABLE_BASS_MIDI, policy.pitchRange)
        val voicing = realizer.analyze(spec, listOf(note))
        return StimulusEvent(voicing = voicing, atMillis = 0, velocities = listOf(settings.velocity))
    }

    /** The instance of [pitchClass] closest to [target], within [range]. */
    private fun nearestNote(pitchClass: SpelledPitchClass, target: Int, range: IntRange): MidiNote {
        val degreeClass = pitchClass.pitchClass.value
        val octaves = range.filter { it.mod(SEMITONES_PER_OCTAVE) == degreeClass }
        return MidiNote(octaves.minByOrNull { abs(it - target) } ?: target.coerceIn(range))
    }

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

    private companion object {
        /** Middle C. The register a reference note or a close voicing is placed nearest to. */
        const val COMFORTABLE_BASS_MIDI = 60
        const val SEMITONES_PER_OCTAVE = 12

        /** How long the reference note rings before the chord it introduces replaces it. */
        const val ANCHOR_DURATION_MILLIS = 700L
    }
}
