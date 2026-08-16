package com.harmonygates.core.music.score

import com.harmonygates.core.music.pitch.MidiNote
import kotlin.math.abs

/**
 * How close to the beat counts as on it.
 *
 * 08_SIGHT_READING_ENGINE.md §5 gives these as "initial tuning values, not fixed laws", which is
 * why they are content rather than constants: a gate chooses a window, and the numbers move when
 * somebody has played against them.
 */
public data class RhythmTolerance(val millis: Long, val label: String) {
    init {
        require(millis > 0) { "A tolerance of zero would fail every human being" }
    }

    public fun accepts(errorMillis: Long): Boolean = abs(errorMillis) <= millis

    public companion object {
        public val LEARN: RhythmTolerance = RhythmTolerance(180, "Learn")
        public val PRACTICE: RhythmTolerance = RhythmTolerance(100, "Practice")
        public val CHALLENGE: RhythmTolerance = RhythmTolerance(60, "Challenge")
    }
}

/** One note the player played, with when it happened. */
public data class TimedNote(
    val note: MidiNote,
    /** Milliseconds from the moment the phrase started. */
    val atMillis: Long,
)

/** How one written event was performed. */
public data class EventReading(
    val event: ScoreEvent,
    /** The expected onset, in milliseconds from the start of the phrase. */
    val expectedMillis: Long,
    val playedMillis: Long?,
    val playedNotes: List<MidiNote>,
    val pitchCorrect: Boolean,
    val timingCorrect: Boolean,
) {
    /** Positive is late, negative is early. Null when nothing was played for this event. */
    public val errorMillis: Long? get() = playedMillis?.let { it - expectedMillis }

    public val wasPlayed: Boolean get() = playedMillis != null
}

/**
 * How a phrase was read.
 *
 * Pitch and rhythm are counted separately and never combined into one number. §5: "Score pitch
 * and rhythm separately so the player can see whether the problem was reading pitch or timing."
 * A single percentage would hide the only thing worth knowing.
 */
public data class ReadingResult(
    val readings: List<EventReading>,
    val tolerance: RhythmTolerance,
) {
    public val expectedCount: Int get() = readings.size

    public val pitchCorrectCount: Int get() = readings.count { it.pitchCorrect }

    public val timingCorrectCount: Int get() = readings.count { it.timingCorrect }

    public val missedCount: Int get() = readings.count { !it.wasPlayed }

    /** 0..1 over the written events. */
    public val pitchAccuracy: Double
        get() = if (expectedCount == 0) 0.0 else pitchCorrectCount.toDouble() / expectedCount

    public val timingAccuracy: Double
        get() = if (expectedCount == 0) 0.0 else timingCorrectCount.toDouble() / expectedCount

    /** Positive means the player runs late overall, negative early. Null if nothing was played. */
    public val medianErrorMillis: Long?
        get() {
            val errors = readings.mapNotNull { it.errorMillis }.sorted()
            return errors.getOrNull(errors.size / 2)
        }

    /**
     * Which of the two the player should work on.
     *
     * The point of scoring them apart: a reader who is playing the right notes late has a
     * different problem from one playing wrong notes in time, and being told "68%" helps
     * neither.
     */
    public val weakness: ReadingWeakness
        get() = when {
            expectedCount == 0 -> ReadingWeakness.NOTHING_READ
            pitchAccuracy >= GOOD && timingAccuracy >= GOOD -> ReadingWeakness.NEITHER
            pitchAccuracy < timingAccuracy -> ReadingWeakness.PITCH
            timingAccuracy < pitchAccuracy -> ReadingWeakness.TIMING
            else -> ReadingWeakness.BOTH
        }

    private companion object {
        const val GOOD = 0.9
    }
}

/** What went less well. */
public enum class ReadingWeakness {
    NEITHER,
    PITCH,
    TIMING,
    BOTH,
    NOTHING_READ,
}

/**
 * A count-in before the phrase starts.
 *
 * 08 §4's measure reading counts in and then evaluates in time, which means the phrase's
 * first beat is not the moment playback began — and every expected onset is measured from the
 * downbeat, not from the click.
 */
public data class CountIn(val beats: Int, val meter: TimeSignature, val tempoBpm: Int) {
    init {
        require(beats >= 0) { "A negative count-in is not a count-in" }
        require(tempoBpm > 0) { "Tempo must be positive" }
    }

    /** How long the count-in lasts. */
    public val durationMillis: Long get() = (meter.beatLength * beats).toMillis(tempoBpm)

    /** Click times, from the moment the count-in starts. */
    public val clickTimesMillis: List<Long>
        get() = (0 until beats).map { (meter.beatLength * it).toMillis(tempoBpm) }

    public companion object {
        public fun oneBar(meter: TimeSignature, tempoBpm: Int): CountIn =
            CountIn(meter.beats, meter, tempoBpm)
    }
}

/**
 * Grades a performance of a written phrase.
 *
 * Phase 9's acceptance criterion is that "generated phrase displays and grades against injected
 * clock", and the injection is the point: the evaluator is handed timestamps rather than reading
 * a clock itself, so a whole performance can be graded in a test with no waiting and no
 * flakiness. §5 asks for "beat-domain expected onsets converted through a monotonic clock", and
 * this is the conversion — once, at the boundary, from exact rational beats into milliseconds.
 */
public class ReadingEvaluator(
    private val tolerance: RhythmTolerance = RhythmTolerance.PRACTICE,
) {

    /**
     * @param played every note the player struck, with the time it arrived, measured from the
     *   downbeat of the phrase. A count-in is subtracted before this is called.
     */
    public fun evaluate(phrase: ScorePhrase, played: List<TimedNote>): ReadingResult {
        val expected = phrase.soundingEvents
        val remaining = played.sortedBy { it.atMillis }.toMutableList()

        val readings = expected.map { event ->
            val expectedMillis = event.onset.toMillis(phrase.tempoBpm)
            val wanted = event.midiNotes.map { it.value }.sorted()

            // Notes are claimed by the event whose written time they are nearest to, within a
            // generous window. Matching by order alone would cascade: one missed note and every
            // note after it is graded against the wrong event.
            val window = tolerance.millis * SEARCH_WINDOW_MULTIPLE
            val candidates = remaining.filter { abs(it.atMillis - expectedMillis) <= window }
            val claimed = claimFor(candidates, wanted)
            remaining -= claimed.toSet()

            val playedNotes = claimed.map { it.note }.sortedBy { it.value }
            val onsetMillis = claimed.minOfOrNull { it.atMillis }
            val pitchCorrect = playedNotes.map { it.value } == wanted
            val timingCorrect = onsetMillis != null && tolerance.accepts(onsetMillis - expectedMillis)

            EventReading(
                event = event,
                expectedMillis = expectedMillis,
                playedMillis = onsetMillis,
                playedNotes = playedNotes,
                pitchCorrect = pitchCorrect,
                // Deliberately independent of pitch. §5 asks for pitch and rhythm to be scored
                // separately "so the player can see whether the problem was reading pitch or
                // timing", and a reader hitting wrong notes exactly on the beat has good time
                // and a pitch problem — which is the single most useful thing to tell them.
                timingCorrect = timingCorrect,
            )
        }

        return ReadingResult(readings, tolerance)
    }

    /**
     * Which of the nearby notes belong to this event.
     *
     * Prefers the notes that were actually written — a chord that was played with one wrong note
     * should be reported as that, not as a missed chord with a stray note beside it.
     */
    private fun claimFor(candidates: List<TimedNote>, wanted: List<Int>): List<TimedNote> {
        if (candidates.isEmpty()) return emptyList()
        val matching = candidates.filter { it.note.value in wanted }
        if (matching.size >= wanted.size) return matching.take(wanted.size)

        // Not enough of the written notes are there, so take the nearest few and let the pitch
        // comparison report what was actually played.
        val nearest = candidates.sortedBy { it.atMillis }.take(wanted.size)
        return (matching + nearest).distinct().take(wanted.size)
    }

    private companion object {
        /** How far outside the tolerance a note may be and still count as an attempt at an event. */
        const val SEARCH_WINDOW_MULTIPLE = 4
    }
}
