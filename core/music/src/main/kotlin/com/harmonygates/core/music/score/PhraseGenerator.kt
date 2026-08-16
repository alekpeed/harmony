package com.harmonygates.core.music.score

import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.pitch.SpelledPitch
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.random.DefaultSeededRandomFactory
import com.harmonygates.core.music.random.RandomSource
import com.harmonygates.core.music.random.SeededRandomFactory

/** What the player is asked to read. */
public enum class ReadingMaterial(public val label: String) {
    /** One note at a time. */
    SINGLE_NOTES("Single notes"),

    /** Two notes together. */
    INTERVALS("Intervals"),

    /** Three notes together. */
    TRIADS("Triads"),
}

/**
 * What a phrase should contain.
 *
 * 08_SIGHT_READING_ENGINE.md §6's adaptive parameters, as data — so making a gate harder is
 * changing numbers in content rather than writing another generator.
 */
public data class PhraseSpec(
    val key: KeyContext,
    val material: ReadingMaterial = ReadingMaterial.SINGLE_NOTES,
    val meter: TimeSignature = TimeSignature.FOUR_FOUR,
    val tempoBpm: Int = DEFAULT_TEMPO,
    val measureCount: Int = DEFAULT_MEASURES,
    val clefs: List<Clef> = listOf(Clef.TREBLE),
    /** The playable range, as MIDI numbers. */
    val range: IntRange = DEFAULT_RANGE,
    /** Note values that may appear. Rational, so a bar always adds up. */
    val durations: List<RationalBeat> = listOf(RationalBeat.QUARTER, RationalBeat.HALF),
    /** 0..1. How often a beat is a rest rather than a note. */
    val restProbability: Double = DEFAULT_REST_PROBABILITY,
    /** Largest leap between consecutive notes, in scale steps. Small is easier to read. */
    val maxLeapSteps: Int = DEFAULT_MAX_LEAP,
) {
    init {
        require(measureCount > 0) { "A phrase needs at least one measure" }
        require(tempoBpm > 0) { "Tempo must be positive: $tempoBpm" }
        require(durations.isNotEmpty()) { "A phrase needs at least one note value" }
        require(restProbability in 0.0..1.0) { "Rest probability is a fraction: $restProbability" }
        require(clefs.isNotEmpty()) { "A phrase needs at least one clef" }
    }

    public companion object {
        public const val DEFAULT_TEMPO: Int = 72
        public const val DEFAULT_MEASURES: Int = 2
        public const val DEFAULT_REST_PROBABILITY: Double = 0.15
        public const val DEFAULT_MAX_LEAP: Int = 4
        public val DEFAULT_RANGE: IntRange = 48..84
    }
}

/** Builds phrases to read. */
public interface PhraseGenerator {
    public fun generate(spec: PhraseSpec, seed: Long): ScorePhrase
}

/**
 * The production phrase generator.
 *
 * Two properties matter more than the music it writes. Every bar holds exactly its meter's worth
 * — durations are rational, so this is arithmetic rather than a tolerance — and the same seed
 * always writes the same phrase, so a player who misread bar two can be shown bar two again.
 *
 * Notes are drawn from the key's own scale, which is what makes the accidentals in the notation
 * mean something: a phrase in Eb that wandered chromatically would be a reading exercise about
 * accidentals rather than about the key.
 */
public class DefaultPhraseGenerator(
    private val randomFactory: SeededRandomFactory = DefaultSeededRandomFactory,
) : PhraseGenerator {

    override fun generate(spec: PhraseSpec, seed: Long): ScorePhrase {
        val random = randomFactory.create(seed)
        val scale = scaleFor(spec)
        val measures = mutableListOf<Measure>()
        var position = RationalBeat.ZERO
        var lastIndex = scale.size / 2

        repeat(spec.measureCount) {
            val events = mutableListOf<ScoreEvent>()
            val barEnd = position + spec.meter.measureLength
            var cursor = position

            while (cursor < barEnd) {
                val remaining = barEnd - cursor
                // Only durations that still fit, so a bar can never overrun. The shortest is
                // always among them, which is what guarantees the loop terminates.
                val fitting = spec.durations.filter { it <= remaining }
                val duration = if (fitting.isEmpty()) remaining else random.pick(fitting)

                if (random.nextDouble() < spec.restProbability) {
                    events += ScoreEvent.Rest(cursor, duration, spec.clefs.first())
                } else {
                    val stepIndex = nextIndex(lastIndex, scale.size, spec.maxLeapSteps, random)
                    lastIndex = stepIndex
                    events += eventFor(spec, scale, stepIndex, cursor, duration, random)
                }
                cursor += duration
            }

            measures += Measure(events, position)
            position = barEnd
        }

        return ScorePhrase(
            key = spec.key,
            meter = spec.meter,
            tempoBpm = spec.tempoBpm,
            measures = measures,
            seed = seed,
        )
    }

    private fun eventFor(
        spec: PhraseSpec,
        scale: List<SpelledPitch>,
        index: Int,
        onset: RationalBeat,
        duration: RationalBeat,
        random: RandomSource,
    ): ScoreEvent {
        val clef = random.pick(spec.clefs)
        val root = scale[index]

        return when (spec.material) {
            ReadingMaterial.SINGLE_NOTES -> ScoreEvent.Note(onset, duration, root, clef)

            // Diatonic thirds and fifths: stacking by scale step rather than by semitone is what
            // keeps an interval inside the key rather than turning it into an accidental.
            ReadingMaterial.INTERVALS -> ScoreEvent.Chord(
                onset,
                duration,
                listOfNotNull(root, scale.getOrNull(index + 2)),
                clef,
            )

            ReadingMaterial.TRIADS -> ScoreEvent.Chord(
                onset,
                duration,
                listOfNotNull(root, scale.getOrNull(index + 2), scale.getOrNull(index + 4)),
                clef,
            )
        }
    }

    /** A step of at most [maxLeap], staying inside the range. Small moves read more easily. */
    private fun nextIndex(from: Int, size: Int, maxLeap: Int, random: RandomSource): Int {
        val lowest = maxOf(0, from - maxLeap)
        val highest = minOf(size - 1, from + maxLeap)
        return random.nextInt(lowest, highest + 1)
    }

    /**
     * The key's scale across the playable range, spelled.
     *
     * Built upward from the tonic so that the spelling follows the key signature: in Eb the
     * fourth degree is Ab and never G#, which is the whole reason the notation is legible.
     */
    private fun scaleFor(spec: PhraseSpec): List<SpelledPitch> {
        val degrees = (1..DEGREES_PER_OCTAVE).mapNotNull { degree ->
            when (val spelled = spec.key.degreeRoot(com.harmonygates.core.music.pitch.ScaleDegree(degree))) {
                is SpellingResult.Spelled -> spelled.value
                is SpellingResult.Overflow -> null
            }
        }
        require(degrees.isNotEmpty()) { "${spec.key} has no writable scale degrees" }

        val pitches = mutableListOf<SpelledPitch>()
        for (octave in LOWEST_OCTAVE..HIGHEST_OCTAVE) {
            degrees.forEach { degree ->
                val pitch = degree.inOctave(octave)
                if (pitch.midiNote.value in spec.range) pitches += pitch
            }
        }
        require(pitches.size > MINIMUM_PITCHES) {
            "${spec.key} has only ${pitches.size} notes inside ${spec.range}; too few to read"
        }
        return pitches.sortedBy { it.midiNote.value }
    }

    private companion object {
        const val DEGREES_PER_OCTAVE = 7
        const val LOWEST_OCTAVE = 1
        const val HIGHEST_OCTAVE = 7

        /** Enough that a leap of four steps has somewhere to land. */
        const val MINIMUM_PITCHES = 8
    }
}
