package com.harmonygates.core.music.relativepitch

import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.eartraining.StimulusSettings
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.random.DefaultSeededRandomFactory
import com.harmonygates.core.music.random.RandomSource
import com.harmonygates.core.music.random.SeededRandomFactory
import com.harmonygates.core.music.realize.ChordRealizer
import com.harmonygates.core.music.realize.ComfortableVoicing
import com.harmonygates.core.music.realize.DefaultChordRealizer

/** One or more notes sounded together, at a time. A chord's worth of notes, or just one. */
public data class ToneEvent(val notes: List<MidiNote>, val atMillis: Long, val velocity: Int) {
    init {
        require(notes.isNotEmpty()) { "An event with no notes is silence" }
        require(atMillis >= 0) { "An event cannot happen before the stimulus starts" }
    }
}

/**
 * What a multiple-choice level plays. Deliberately lighter than `eartraining.StimulusSpec`: a
 * bare interval or a scale degree has no [ChordSpec] behind it, so forcing one through that type
 * would mean inventing a fake chord for two arbitrary notes rather than admitting there isn't one.
 */
public data class RelativePitchStimulus(
    val seed: Long,
    val events: List<ToneEvent>,
    val instrumentId: String,
    val tempoBpm: Int,
) {
    init {
        require(events.isNotEmpty()) { "A stimulus with nothing to play teaches nothing" }
        require(tempoBpm > 0) { "Tempo must be positive: $tempoBpm" }
    }
}

/** One multiple-choice question: what was played, the right answer, and the options to show. */
public data class RelativePitchExercise(
    val level: RelativePitchLevel,
    val stimulus: RelativePitchStimulus,
    val correctChoiceId: String,
    val choiceIds: List<String>,
)

public interface RelativePitchExerciseGenerator {
    /** Null for [RelativePitchTier.FUNCTION_HEARING] and [RelativePitchTier.REPRODUCE] — those
     * tiers route into the existing `eartraining` generator and screen instead. */
    public fun generate(
        level: RelativePitchLevel,
        seed: Long,
        settings: StimulusSettings = StimulusSettings(),
    ): RelativePitchExercise?
}

/**
 * Deterministic from the seed, the same guarantee `eartraining`'s generator makes and for the
 * same reason: a level a player got wrong has to be replayable exactly, not just "a similar one".
 */
public class DefaultRelativePitchExerciseGenerator(
    private val realizer: ChordRealizer = DefaultChordRealizer(),
    private val randomFactory: SeededRandomFactory = DefaultSeededRandomFactory,
) : RelativePitchExerciseGenerator {

    override fun generate(level: RelativePitchLevel, seed: Long, settings: StimulusSettings): RelativePitchExercise? {
        val random = randomFactory.create(seed)
        return when (level.tier) {
            RelativePitchTier.INTERVALS -> interval(level, seed, settings, random)
            RelativePitchTier.SCALE_DEGREES -> degree(level, seed, settings, random)
            RelativePitchTier.CHORD_QUALITY -> quality(level, seed, settings, random)
            RelativePitchTier.FUNCTION_HEARING, RelativePitchTier.REPRODUCE -> null
        }
    }

    /** A reference note, then a second note the chosen interval away. Which interval was it? */
    private fun interval(
        level: RelativePitchLevel,
        seed: Long,
        settings: StimulusSettings,
        random: RandomSource,
    ): RelativePitchExercise? {
        val choices = level.intervalChoices
        if (choices.size < 2) return null
        val answer = random.pick(choices)
        val ascending = answer == IntervalClass.UNISON || random.pick(listOf(true, false))
        val reference = random.pick(REFERENCE_NOTES)
        val target = if (ascending) reference + answer.semitones else reference - answer.semitones

        val events = listOf(
            ToneEvent(listOf(MidiNote(reference)), 0, settings.velocity),
            ToneEvent(listOf(MidiNote(target)), NOTE_GAP_MILLIS, settings.velocity),
        )
        return RelativePitchExercise(
            level = level,
            stimulus = stimulusOf(seed, events, settings, random),
            correctChoiceId = answer.name,
            choiceIds = random.shuffled(choices.map { it.name }),
        )
    }

    /** A tonic, then one note. Which scale degree was it? */
    private fun degree(
        level: RelativePitchLevel,
        seed: Long,
        settings: StimulusSettings,
        random: RandomSource,
    ): RelativePitchExercise? {
        val choices = level.degreeChoices
        if (choices.size < 2) return null
        val tonic = random.pick(ROOTS)
        val tonicNote = ComfortableVoicing.nearestNote(tonic, DEFAULT_RANGE).value
        val answer = random.pick(choices)
        val target = tonicNote + answer.semitonesFromTonic

        val events = listOf(
            ToneEvent(listOf(MidiNote(tonicNote)), 0, settings.velocity),
            ToneEvent(listOf(MidiNote(target)), NOTE_GAP_MILLIS, settings.velocity),
        )
        return RelativePitchExercise(
            level = level,
            stimulus = stimulusOf(seed, events, settings, random),
            correctChoiceId = answer.name,
            choiceIds = random.shuffled(choices.map { it.name }),
        )
    }

    /** One chord. Which quality — by ear, nothing reproduced. */
    private fun quality(
        level: RelativePitchLevel,
        seed: Long,
        settings: StimulusSettings,
        random: RandomSource,
    ): RelativePitchExercise? {
        val choices = level.qualityChoices
        if (choices.size < 2) return null
        val answer = random.pick(choices)
        val root = random.pick(ROOTS)
        val chord = ChordSpec(root, answer.formula.id)
        if (realizer.trySpell(chord) !is SpellingResult.Spelled) return null
        val voicing = ComfortableVoicing.preferredVoicing(chord, realizer, DEFAULT_RANGE) ?: return null

        val events = listOf(ToneEvent(voicing.pitches, 0, settings.velocity))
        return RelativePitchExercise(
            level = level,
            stimulus = stimulusOf(seed, events, settings, random),
            correctChoiceId = answer.name,
            choiceIds = random.shuffled(choices.map { it.name }),
        )
    }

    private fun stimulusOf(
        seed: Long,
        events: List<ToneEvent>,
        settings: StimulusSettings,
        random: RandomSource,
    ) = RelativePitchStimulus(
        seed = seed,
        events = events,
        instrumentId = random.pick(settings.instrumentIds),
        tempoBpm = settings.tempoBpm,
    )

    private companion object {
        /** Two octaves either side of middle C — the same span `ChordRealizer` defaults to. */
        val DEFAULT_RANGE = 36..96

        /** How long the reference note rings before the second note replaces it. */
        const val NOTE_GAP_MILLIS = 700L

        /**
         * A band comfortably in reach of both hands, narrow enough that even an octave-plus
         * interval never leaves [DEFAULT_RANGE]: the lowest reference (52) minus a full octave
         * (12) is 40, still inside 36..96, and the same headroom holds going up from the highest.
         */
        val REFERENCE_NOTES = (52..68).toList()

        val ROOTS: List<SpelledPitchClass> = listOf(
            "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B",
        ).map { requireNotNull(SpelledPitchClass.parseOrNull(it)) }
    }
}
