package com.harmonygates.core.music.voicing

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitch

/**
 * Which chord tone is in the bass.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §7: inversion follows from the bass note's relationship to the
 * chord, not from list order. Extended chords collapse to [OTHER] plus an explicit bass
 * degree, because "fifth inversion of a thirteenth chord" teaches nobody anything.
 */
public enum class Inversion {
    ROOT,
    FIRST,
    SECOND,
    THIRD,
    OTHER,
    ;

    public companion object {
        /** Maps a bass degree onto the conventional inversion names. */
        public fun forBassDegree(degree: ChordDegree?): Inversion = when (degree?.number) {
            null -> OTHER
            1 -> ROOT
            2, 3, 4 -> FIRST
            5 -> SECOND
            6, 7 -> THIRD
            else -> OTHER
        }
    }
}

/** Named voicing shapes the curriculum teaches. */
public enum class VoicingFamily {
    /** All tones inside one octave above the bass. */
    CLOSE,

    /** Close position with the second voice from the top dropped an octave. */
    DROP_2,

    /** Close position with the third voice from the top dropped an octave. */
    DROP_3,

    /** Close position with the second and fourth voices from the top dropped an octave. */
    DROP_2_AND_4,

    /** Wide open spacing, typically root and seventh in the left hand. */
    SPREAD,

    /** Root, third, seventh. Region 4 of the curriculum. */
    SHELL_1_3_7,

    /** Root, seventh, third. The other common shell inversion. */
    SHELL_1_7_3,

    /** Third and seventh only. */
    GUIDE_TONES,

    /** Rootless A position: 3-5-7-9 for minor, 3-13-7-9 for dominant. */
    ROOTLESS_A,

    /** Rootless B position: 7-9-3-5 for minor, 7-9-3-13 for dominant. */
    ROOTLESS_B,

    /** Stacked fourths. Region 9. */
    QUARTAL,

    /** Anything deliberately spaced that no other family names. */
    OPEN,
}

/** One sounding voice: a written pitch and the chord role it fills, if any. */
public data class VoicedTone(
    val pitch: SpelledPitch,
    /** Null when the note is not a chord tone, e.g. the A of a `C/A` slash bass. */
    val degree: ChordDegree?,
)

/**
 * Facts about a realised voicing that the UI and evaluator would otherwise recompute.
 *
 * Everything here is derived from the voiced tones, so it can never disagree with them.
 */
public data class VoicingMetadata(
    val inversion: Inversion,
    val bassDegree: ChordDegree?,
    val topDegree: ChordDegree?,
    /** Chord role of each voice, bass first. Parallel to `Voicing.pitches`. */
    val degreesByVoice: List<ChordDegree?>,
    val includedDegrees: Set<ChordDegree>,
    val omittedDegrees: Set<ChordDegree>,
    val doubledDegrees: Set<ChordDegree>,
    val family: VoicingFamily?,
    /** Semitones from lowest to highest note. */
    val spanSemitones: Int,
) {
    /** True when the chord's own root is absent, as in a Region 7 rootless voicing. */
    public val isRootless: Boolean get() = includedDegrees.none { it.number == 1 }

    /** Notes that belong to no chord degree, e.g. an independent slash bass. */
    public val nonChordVoiceCount: Int get() = degreesByVoice.count { it == null }
}

/**
 * A chord as actually played: specific notes, in a specific register, in a specific order.
 *
 * [pitches] is ascending and may contain duplicates, because a doubled voice is musically
 * different from a single one and a `Set` would silently discard that
 * (06_PERFORMANCE_EVALUATION_AND_SCORING.md §4).
 *
 * Build one with [voicingOf] rather than the constructor so metadata stays derived.
 */
public data class Voicing(
    val chord: ChordSpec,
    val pitches: List<MidiNote>,
    val spelledPitches: List<SpelledPitch>,
    val metadata: VoicingMetadata,
) {
    init {
        require(pitches.isNotEmpty()) { "A voicing must contain at least one note" }
        require(pitches.size == spelledPitches.size) {
            "Every pitch needs a spelling: ${pitches.size} pitches, ${spelledPitches.size} spellings"
        }
        require(pitches.size == metadata.degreesByVoice.size) {
            "Every pitch needs a degree slot: ${pitches.size} pitches, ${metadata.degreesByVoice.size} slots"
        }
        require(pitches == pitches.sorted()) { "Voicing pitches must be ascending: $pitches" }
    }

    public val bass: MidiNote get() = pitches.first()

    public val top: MidiNote get() = pitches.last()

    public val voiceCount: Int get() = pitches.size

    /** The voices as (pitch, role) pairs, bass first. */
    public val tones: List<VoicedTone>
        get() = spelledPitches.mapIndexed { index, pitch -> VoicedTone(pitch, metadata.degreesByVoice[index]) }

    override fun toString(): String = "${chord.symbol} [${spelledPitches.joinToString(" ")}]"
}

/**
 * Assembles a [Voicing] from voiced tones, deriving every piece of metadata.
 *
 * Sorting happens here so that a transformation which drops a voice below the bass still
 * yields a well-formed ascending voicing.
 */
public fun voicingOf(
    chord: ChordSpec,
    tones: List<VoicedTone>,
    family: VoicingFamily? = null,
): Voicing {
    require(tones.isNotEmpty()) { "A voicing must contain at least one note" }
    val ascending = tones.sortedBy { it.pitch.midiNote.value }
    val degrees = ascending.map { it.degree }
    val included = degrees.filterNotNull().toSet()
    val doubled = degrees.filterNotNull()
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
    val pitches = ascending.map { it.pitch.midiNote }

    return Voicing(
        chord = chord,
        pitches = pitches,
        spelledPitches = ascending.map { it.pitch },
        metadata = VoicingMetadata(
            inversion = Inversion.forBassDegree(degrees.first()),
            bassDegree = degrees.first(),
            topDegree = degrees.last(),
            degreesByVoice = degrees,
            includedDegrees = included,
            omittedDegrees = chord.degrees.toSet() - included,
            doubledDegrees = doubled,
            family = family,
            spanSemitones = pitches.last().value - pitches.first().value,
        ),
    )
}
