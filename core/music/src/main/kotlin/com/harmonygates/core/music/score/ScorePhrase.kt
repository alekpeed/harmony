package com.harmonygates.core.music.score

import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitch

/** Which staff an event is written on. */
public enum class Clef(public val label: String) {
    TREBLE("Treble"),
    BASS("Bass"),
    ;

    /**
     * The letter and octave that sit on the middle line.
     *
     * B4 for treble, D3 for bass. Every vertical position is measured from here, which is the
     * whole of staff placement in one number per clef. Diatonic rather than chromatic, because
     * the staff is: F and F sharp share a line.
     */
    internal val middleLineDiatonicIndex: Int
        get() = if (this == TREBLE) TREBLE_MIDDLE else BASS_MIDDLE

    private companion object {
        /** B4: octave 4, letter B (index 6). */
        const val TREBLE_MIDDLE = 4 * 7 + 6

        /** D3: octave 3, letter D (index 1). */
        const val BASS_MIDDLE = 3 * 7 + 1
    }
}

/** Something that happens at a point in a phrase. */
public sealed interface ScoreEvent {
    /** Position from the start of the phrase, in quarter notes. */
    public val onset: RationalBeat

    public val duration: RationalBeat

    /** Where the event ends. */
    public val end: RationalBeat get() = onset + duration

    /** One pitch. */
    public data class Note(
        override val onset: RationalBeat,
        override val duration: RationalBeat,
        val pitch: SpelledPitch,
        val clef: Clef = Clef.TREBLE,
        val tiedToNext: Boolean = false,
        val fingering: Int? = null,
    ) : ScoreEvent {
        override val pitches: List<SpelledPitch> get() = listOf(pitch)
    }

    /** Several pitches struck together. */
    public data class Chord(
        override val onset: RationalBeat,
        override val duration: RationalBeat,
        override val pitches: List<SpelledPitch>,
        val clef: Clef = Clef.TREBLE,
        /** The symbol written above the staff, when there is one. */
        val symbol: ChordSpec? = null,
    ) : ScoreEvent {
        init {
            require(pitches.isNotEmpty()) { "A chord with no pitches is a rest" }
        }
    }

    /** Silence. */
    public data class Rest(
        override val onset: RationalBeat,
        override val duration: RationalBeat,
        val clef: Clef = Clef.TREBLE,
    ) : ScoreEvent {
        override val pitches: List<SpelledPitch> get() = emptyList()
    }

    /** Every pitch this event sounds. Empty for a rest. */
    public val pitches: List<SpelledPitch>

    public val midiNotes: List<MidiNote> get() = pitches.map { it.midiNote }
}

/** One bar. */
public data class Measure(
    val events: List<ScoreEvent>,
    /** Where the bar starts, from the beginning of the phrase. */
    val start: RationalBeat = RationalBeat.ZERO,
) {
    public val end: RationalBeat
        get() = events.maxOfOrNull { it.end } ?: start

    /** Events that sound, in time order. Rests are notation, not performance. */
    public val soundingEvents: List<ScoreEvent>
        get() = events.filterNot { it is ScoreEvent.Rest }.sortedBy { it.onset }
}

/**
 * A phrase to read.
 *
 * 08_SIGHT_READING_ENGINE.md §3's model. The phrase carries its own key, meter and tempo because
 * those are what the notation shows and what the rhythm is judged against — a phrase handed to
 * an evaluator without them could be graded at the wrong speed.
 */
public data class ScorePhrase(
    val key: KeyContext,
    val meter: TimeSignature,
    val tempoBpm: Int,
    val measures: List<Measure>,
    /** The seed this was generated from, so a misread bar can be reproduced exactly. */
    val seed: Long = 0,
) {
    init {
        require(measures.isNotEmpty()) { "A phrase needs at least one measure" }
        require(tempoBpm > 0) { "Tempo must be positive: $tempoBpm" }
    }

    /** Every event in the phrase, in time order. */
    public val events: List<ScoreEvent>
        get() = measures.flatMap { it.events }.sortedBy { it.onset }

    /** Every event that sounds, in time order. What the player is expected to play. */
    public val soundingEvents: List<ScoreEvent>
        get() = events.filterNot { it is ScoreEvent.Rest }

    public val length: RationalBeat
        get() = measures.maxOfOrNull { it.end } ?: RationalBeat.ZERO

    public val durationMillis: Long get() = length.toMillis(tempoBpm)

    /**
     * Checks that every bar holds exactly its meter's worth.
     *
     * A generator that overruns a bar produces notation that cannot be engraved and rhythm that
     * cannot be counted, and both failures are much easier to understand here than three layers
     * later.
     */
    public fun validate(): List<String> = measures.mapIndexedNotNull { index, measure ->
        val filled = measure.events.fold(RationalBeat.ZERO) { total, event -> total + event.duration }
        val expected = meter.measureLength
        if (filled != expected) {
            "Measure ${index + 1} holds $filled beats; $meter needs exactly $expected"
        } else {
            null
        }
    }
}

/**
 * Where a pitch sits on a staff.
 *
 * Vertical position is diatonic, not chromatic: F and F# occupy the same line and differ by an
 * accidental. That is why this is computed from the letter and the octave rather than from the
 * MIDI number — a renderer working from pitch alone would draw C# and Db in different places,
 * and they are the same key and the same note head one line apart only when spelled that way.
 */
public object StaffPlacement {

    /**
     * Steps above the middle line. Positive is up; each step is a line or a space.
     *
     * The middle line is 0, the space above it 1, the line above that 2.
     */
    public fun stepsFromMiddleLine(pitch: SpelledPitch, clef: Clef): Int =
        diatonicIndex(pitch) - clef.middleLineDiatonicIndex

    /** How many ledger lines this pitch needs, and on which side. Zero when it sits on the staff. */
    public fun ledgerLines(pitch: SpelledPitch, clef: Clef): Int {
        val steps = stepsFromMiddleLine(pitch, clef)
        return when {
            steps > STAFF_HALF_HEIGHT -> (steps - STAFF_HALF_HEIGHT) / 2
            steps < -STAFF_HALF_HEIGHT -> (-steps - STAFF_HALF_HEIGHT) / 2
            else -> 0
        }
    }

    /** True when a note is high or low enough to need lines drawn for it. */
    public fun needsLedgerLines(pitch: SpelledPitch, clef: Clef): Boolean =
        ledgerLines(pitch, clef) > 0

    /**
     * The clef this pitch reads most easily on.
     *
     * Middle C is the boundary. A grand staff writes anything below it in the bass, which is
     * what keeps ledger lines off both staves for ordinary material.
     */
    public fun preferredClef(pitch: SpelledPitch): Clef =
        if (pitch.midiNote.value < MIDDLE_C) Clef.BASS else Clef.TREBLE

    /** A pitch's position in the diatonic sequence, counting every letter as one step. */
    private fun diatonicIndex(pitch: SpelledPitch): Int =
        pitch.octave * LETTERS_PER_OCTAVE + pitch.pitchClass.letter.ordinal

    private const val LETTERS_PER_OCTAVE = 7

    /** Four steps either side of the middle line is the five-line staff. */
    private const val STAFF_HALF_HEIGHT = 4

    private const val MIDDLE_C = 60
}
