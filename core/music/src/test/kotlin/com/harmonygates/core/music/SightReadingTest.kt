package com.harmonygates.core.music

import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.score.Clef
import com.harmonygates.core.music.score.CountIn
import com.harmonygates.core.music.score.DefaultPhraseGenerator
import com.harmonygates.core.music.score.PhraseSpec
import com.harmonygates.core.music.score.RationalBeat
import com.harmonygates.core.music.score.ReadingEvaluator
import com.harmonygates.core.music.score.ReadingMaterial
import com.harmonygates.core.music.score.ReadingWeakness
import com.harmonygates.core.music.score.RhythmTolerance
import com.harmonygates.core.music.score.ScorePhrase
import com.harmonygates.core.music.score.StaffPlacement
import com.harmonygates.core.music.score.TimeSignature
import com.harmonygates.core.music.score.TimedNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sight reading.
 *
 * Phase 9's acceptance criterion is that a "generated phrase displays and grades against
 * injected clock". The grading half is tested here exactly — the evaluator is handed timestamps
 * rather than reading a clock, so a whole performance is graded instantly and identically every
 * run.
 */
class SightReadingTest {

    private val generator = DefaultPhraseGenerator()
    private fun key(name: String) = KeyContext(requireNotNull(SpelledPitchClass.parseOrNull(name)))

    private fun spec(
        material: ReadingMaterial = ReadingMaterial.SINGLE_NOTES,
        meter: TimeSignature = TimeSignature.FOUR_FOUR,
        measures: Int = 2,
        tonic: String = "C",
    ) = PhraseSpec(key = key(tonic), material = material, meter = meter, measureCount = measures)

    /** Plays every written event exactly on time and exactly right. */
    private fun perfectPerformance(phrase: ScorePhrase): List<TimedNote> =
        phrase.soundingEvents.flatMap { event ->
            event.midiNotes.map { TimedNote(it, event.onset.toMillis(phrase.tempoBpm)) }
        }

    // --- Rational durations ----------------------------------------------------------------------

    @Test
    fun `three triplets make exactly one beat`() {
        val triplet = RationalBeat.TRIPLET_EIGHTH
        val total = triplet + triplet + triplet

        // The reason 08 §3 forbids floating-point durations: in doubles this is 0.9999999999999999.
        assertEquals(RationalBeat.QUARTER, total)
        assertTrue(total == RationalBeat.QUARTER, "Exact equality, not approximate")
    }

    @Test
    fun `a dotted quarter is exactly three eighths`() {
        assertEquals(
            RationalBeat.EIGHTH + RationalBeat.EIGHTH + RationalBeat.EIGHTH,
            RationalBeat.QUARTER.dotted(),
        )
    }

    @Test
    fun `a double dot adds a quarter as well as a half`() {
        // A double-dotted half is a half plus a quarter plus an eighth: seven eighths.
        assertEquals(RationalBeat.of(7, 2), RationalBeat.HALF.dotted(dots = 2))
    }

    @Test
    fun `durations reduce so equality is value equality`() {
        assertEquals(RationalBeat.QUARTER, RationalBeat.of(4, 4))
        assertEquals(RationalBeat.EIGHTH, RationalBeat.of(8, 16))
        assertEquals(RationalBeat.of(1, 2), RationalBeat.of(-1, -2), "A negative denominator normalises")
    }

    @Test
    fun `a bar of four four is four quarters`() {
        assertEquals(RationalBeat.of(4), TimeSignature.FOUR_FOUR.measureLength)
        assertEquals(RationalBeat.of(3), TimeSignature.THREE_FOUR.measureLength)
        // 6/8 is six eighths, which is three quarters' worth.
        assertEquals(RationalBeat.of(3), TimeSignature.SIX_EIGHT.measureLength)
        assertEquals(RationalBeat.EIGHTH, TimeSignature.SIX_EIGHT.beatLength)
    }

    @Test
    fun `beats become milliseconds only at the boundary`() {
        // A quarter at 60 bpm is one second, by definition.
        assertEquals(1_000L, RationalBeat.QUARTER.toMillis(60))
        assertEquals(500L, RationalBeat.QUARTER.toMillis(120))
        assertEquals(2_000L, RationalBeat.HALF.toMillis(60))
    }

    // --- Generation --------------------------------------------------------------------------------

    @Test
    fun `every generated bar holds exactly its meter`() {
        listOf(TimeSignature.FOUR_FOUR, TimeSignature.THREE_FOUR, TimeSignature.SIX_EIGHT).forEach { meter ->
            (1L..25L).forEach { seed ->
                val phrase = generator.generate(spec(meter = meter, measures = 3), seed)
                assertEquals(
                    emptyList(),
                    phrase.validate(),
                    "Seed $seed in $meter produced a bar that does not add up",
                )
            }
        }
    }

    @Test
    fun `the same seed writes the same phrase`() {
        val first = generator.generate(spec(), seed = 77)
        val second = generator.generate(spec(), seed = 77)

        assertEquals(first, second, "A misread bar has to be reproducible")
    }

    @Test
    fun `a phrase stays inside the key it is written in`() {
        // Eb major: every note should be spelled with flats, never G# for Ab.
        val phrase = generator.generate(spec(tonic = "Eb"), seed = 5)
        val spellings = phrase.soundingEvents.flatMap { it.pitches }.map { it.pitchClass.toString() }

        assertTrue(
            spellings.none { it.contains("#") },
            "A phrase in Eb should not need sharps: ${spellings.distinct()}",
        )
    }

    @Test
    fun `intervals and triads sound the right number of notes`() {
        val interval = generator.generate(spec(material = ReadingMaterial.INTERVALS), seed = 3)
        val triad = generator.generate(spec(material = ReadingMaterial.TRIADS), seed = 3)

        assertTrue(interval.soundingEvents.all { it.pitches.size in 1..2 }, "Intervals are two notes")
        assertTrue(triad.soundingEvents.any { it.pitches.size == 3 }, "Triads should reach three")
    }

    @Test
    fun `a phrase knows how long it lasts`() {
        val phrase = generator.generate(spec(measures = 2), seed = 1)

        // Two bars of 4/4 at 72 bpm: eight quarters, each 60000/72 ms.
        assertEquals(RationalBeat.of(8), phrase.length)
        assertEquals(8 * 60_000L / 72, phrase.durationMillis)
    }

    // --- Staff placement ----------------------------------------------------------------------------

    @Test
    fun `the middle line is where each clef says it is`() {
        val b4 = requireNotNull(SpelledPitchClass.parseOrNull("B")).inOctave(4)
        val d3 = requireNotNull(SpelledPitchClass.parseOrNull("D")).inOctave(3)

        assertEquals(0, StaffPlacement.stepsFromMiddleLine(b4, Clef.TREBLE))
        assertEquals(0, StaffPlacement.stepsFromMiddleLine(d3, Clef.BASS))
    }

    @Test
    fun `a sharp sits on the same line as the natural`() {
        val f = requireNotNull(SpelledPitchClass.parseOrNull("F")).inOctave(4)
        val fSharp = requireNotNull(SpelledPitchClass.parseOrNull("F#")).inOctave(4)

        // The staff is diatonic. F and F# are one line and an accidental, not two positions.
        assertEquals(
            StaffPlacement.stepsFromMiddleLine(f, Clef.TREBLE),
            StaffPlacement.stepsFromMiddleLine(fSharp, Clef.TREBLE),
        )
    }

    @Test
    fun `enharmonics that are spelled differently are written differently`() {
        val gSharp = requireNotNull(SpelledPitchClass.parseOrNull("G#")).inOctave(4)
        val aFlat = requireNotNull(SpelledPitchClass.parseOrNull("Ab")).inOctave(4)

        // Same key on the piano, different line on the page. A renderer working from MIDI alone
        // would put them in the same place and be wrong.
        assertEquals(gSharp.midiNote, aFlat.midiNote)
        assertTrue(
            StaffPlacement.stepsFromMiddleLine(gSharp, Clef.TREBLE) !=
                StaffPlacement.stepsFromMiddleLine(aFlat, Clef.TREBLE),
        )
    }

    @Test
    fun `notes on the staff need no ledger lines and notes beyond it do`() {
        val e4 = requireNotNull(SpelledPitchClass.parseOrNull("E")).inOctave(4)
        val c4 = requireNotNull(SpelledPitchClass.parseOrNull("C")).inOctave(4)
        val c6 = requireNotNull(SpelledPitchClass.parseOrNull("C")).inOctave(6)

        assertFalse(StaffPlacement.needsLedgerLines(e4, Clef.TREBLE), "E4 is the bottom line")
        assertTrue(StaffPlacement.needsLedgerLines(c4, Clef.TREBLE), "Middle C hangs below the treble staff")
        assertTrue(StaffPlacement.ledgerLines(c6, Clef.TREBLE) >= 2, "C6 is well above it")
    }

    @Test
    fun `a grand staff splits at middle C`() {
        val b3 = requireNotNull(SpelledPitchClass.parseOrNull("B")).inOctave(3)
        val c4 = requireNotNull(SpelledPitchClass.parseOrNull("C")).inOctave(4)

        assertEquals(Clef.BASS, StaffPlacement.preferredClef(b3))
        assertEquals(Clef.TREBLE, StaffPlacement.preferredClef(c4))
    }

    // --- Grading against an injected clock -----------------------------------------------------------

    @Test
    fun `a perfect performance grades perfectly`() {
        val phrase = generator.generate(spec(), seed = 12)
        val result = ReadingEvaluator().evaluate(phrase, perfectPerformance(phrase))

        assertEquals(1.0, result.pitchAccuracy)
        assertEquals(1.0, result.timingAccuracy)
        assertEquals(0, result.missedCount)
        assertEquals(ReadingWeakness.NEITHER, result.weakness)
    }

    @Test
    fun `right notes played late are a timing problem, not a pitch one`() {
        val phrase = generator.generate(spec(), seed = 12)
        val late = perfectPerformance(phrase).map { it.copy(atMillis = it.atMillis + 150) }

        val result = ReadingEvaluator(RhythmTolerance.PRACTICE).evaluate(phrase, late)

        assertEquals(1.0, result.pitchAccuracy, "Every note was the written one")
        assertEquals(0.0, result.timingAccuracy, "And every one was 150 ms late against a 100 ms window")
        assertEquals(ReadingWeakness.TIMING, result.weakness)
        assertEquals(150L, result.medianErrorMillis)
    }

    @Test
    fun `a wider tolerance forgives the same performance`() {
        val phrase = generator.generate(spec(), seed = 12)
        val late = perfectPerformance(phrase).map { it.copy(atMillis = it.atMillis + 150) }

        val learn = ReadingEvaluator(RhythmTolerance.LEARN).evaluate(phrase, late)

        assertEquals(1.0, learn.timingAccuracy, "180 ms accepts a 150 ms lag")
    }

    @Test
    fun `wrong notes in time are a pitch problem`() {
        val phrase = generator.generate(spec(), seed = 15)
        // A semitone off, every time, but exactly on the beat.
        val wrong = perfectPerformance(phrase).map {
            it.copy(note = MidiNote(it.note.value + 1))
        }

        val result = ReadingEvaluator().evaluate(phrase, wrong)

        assertEquals(0.0, result.pitchAccuracy)
        assertEquals(ReadingWeakness.PITCH, result.weakness)
    }

    @Test
    fun `playing nothing is graded as read nothing rather than as an error`() {
        val phrase = generator.generate(spec(), seed = 4)
        val result = ReadingEvaluator().evaluate(phrase, emptyList())

        assertEquals(phrase.soundingEvents.size, result.missedCount)
        assertEquals(0.0, result.pitchAccuracy)
        assertTrue(result.readings.none { it.wasPlayed })
        assertNull(result.medianErrorMillis)
    }

    @Test
    fun `one missed note does not derail every note after it`() {
        val phrase = generator.generate(spec(measures = 2), seed = 19)
        val perfect = perfectPerformance(phrase)
        // Drop whatever belongs to the first written event.
        val firstOnset = phrase.soundingEvents.first().onset.toMillis(phrase.tempoBpm)
        val missingFirst = perfect.filterNot { it.atMillis == firstOnset }

        val result = ReadingEvaluator().evaluate(phrase, missingFirst)

        assertEquals(1, result.missedCount, "Exactly one event should be missing")
        assertTrue(
            result.pitchCorrectCount >= phrase.soundingEvents.size - 1,
            "Matching by nearest time, not by order, so the rest still line up",
        )
    }

    @Test
    fun `grading is deterministic and needs no clock`() {
        val phrase = generator.generate(spec(), seed = 31)
        val performance = perfectPerformance(phrase).map { it.copy(atMillis = it.atMillis + 40) }
        val evaluator = ReadingEvaluator()

        assertEquals(evaluator.evaluate(phrase, performance), evaluator.evaluate(phrase, performance))
    }

    @Test
    fun `early and late are told apart`() {
        val phrase = generator.generate(spec(), seed = 8)
        val early = perfectPerformance(phrase).map { it.copy(atMillis = it.atMillis - 200) }

        val result = ReadingEvaluator().evaluate(phrase, early)

        assertEquals(-200L, result.medianErrorMillis, "Rushing and dragging are different faults")
    }

    // --- Count-in ------------------------------------------------------------------------------------

    @Test
    fun `a count-in is one bar of clicks`() {
        val countIn = CountIn.oneBar(TimeSignature.FOUR_FOUR, tempoBpm = 60)

        assertEquals(4, countIn.clickTimesMillis.size)
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), countIn.clickTimesMillis)
        assertEquals(4_000L, countIn.durationMillis)
    }

    @Test
    fun `a count-in in six eight clicks in eighths`() {
        val countIn = CountIn.oneBar(TimeSignature.SIX_EIGHT, tempoBpm = 120)

        assertEquals(6, countIn.clickTimesMillis.size)
        // An eighth at 120 bpm quarter-note tempo is 250 ms.
        assertEquals(250L, countIn.clickTimesMillis[1])
    }

    @Test
    fun `no count-in is allowed and lasts no time`() {
        val none = CountIn(0, TimeSignature.FOUR_FOUR, 90)

        assertEquals(0L, none.durationMillis)
        assertEquals(emptyList(), none.clickTimesMillis)
    }

    @Test
    fun `a tolerance of zero is refused`() {
        assertNull(
            runCatching { RhythmTolerance(0, "Impossible") }.getOrNull(),
            "A window nobody can hit is not a difficulty setting",
        )
    }

    @Test
    fun `an overrun bar is reported rather than drawn`() {
        val phrase = ScorePhrase(
            key = key("C"),
            meter = TimeSignature.FOUR_FOUR,
            tempoBpm = 90,
            measures = listOf(
                com.harmonygates.core.music.score.Measure(
                    listOf(
                        com.harmonygates.core.music.score.ScoreEvent.Rest(
                            RationalBeat.ZERO,
                            RationalBeat.of(5),
                        ),
                    ),
                ),
            ),
        )

        val problems = phrase.validate()
        assertEquals(1, problems.size)
        assertTrue(problems.first().contains("Measure 1"), problems.first())
    }

    @Test
    fun `a phrase carries the seed that wrote it`() {
        assertEquals(64L, generator.generate(spec(), seed = 64).seed)
        assertNotNull(generator.generate(spec(), seed = 64).key)
    }
}
