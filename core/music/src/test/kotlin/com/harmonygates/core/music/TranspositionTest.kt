package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.StandardRoots
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledInterval
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.pitch.transposeBy
import com.harmonygates.core.music.random.DefaultSeededRandomFactory
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.transform.Transposition
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.core.music.voicing.VoicingPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Transposition invariants, including the randomised property tests
 * 14_TESTING_AND_QUALITY.md §4 asks for.
 *
 * The seeded generator means a failure is reproducible: the seed is printed with the failure
 * and re-running with it replays exactly the same case.
 */
class TranspositionTest {

    private val realizer = DefaultChordRealizer()

    private val intervals = listOf(
        SpelledInterval.MINOR_SECOND,
        SpelledInterval.MAJOR_SECOND,
        SpelledInterval.MINOR_THIRD,
        SpelledInterval.MAJOR_THIRD,
        SpelledInterval.PERFECT_FOURTH,
        SpelledInterval.AUGMENTED_FOURTH,
        SpelledInterval.PERFECT_FIFTH,
        SpelledInterval.MINOR_SIXTH,
        SpelledInterval.MAJOR_SIXTH,
        SpelledInterval.MINOR_SEVENTH,
        SpelledInterval.MAJOR_SEVENTH,
        SpelledInterval.PERFECT_OCTAVE,
    )

    @Test
    fun `transposing by an interval and back returns the original chord`() {
        val chords = ChordFormulas.all.flatMap { formula ->
            StandardRoots.map { ChordSpec(root = it, formulaId = formula.id) }
        }

        for (chord in chords) {
            for (interval in intervals) {
                val up = Transposition.transpose(chord, interval)
                if (up !is SpellingResult.Spelled) continue
                val back = Transposition.transpose(up.value, -interval)
                val restored = assertIs<SpellingResult.Spelled<ChordSpec>>(back)
                assertEquals(
                    chord,
                    restored.value,
                    "${chord.symbol} transposed by $interval and back became ${restored.value.symbol}",
                )
            }
        }
    }

    @Test
    fun `transposing preserves the interval structure of the chord`() {
        val chords = ChordFormulas.all.map { ChordSpec(SpelledPitchClass.C, it.id) }

        for (chord in chords) {
            val original = realizer.chordTones(chord).map { it.pitchClass.value }
            for (interval in intervals) {
                val moved = Transposition.transpose(chord, interval)
                if (moved !is SpellingResult.Spelled) continue
                val transposed = realizer.chordTones(moved.value).map { it.pitchClass.value }
                assertEquals(
                    original.map { (it + interval.semitones).mod(12) },
                    transposed,
                    "${chord.symbol} by $interval",
                )
            }
        }
    }

    @Test
    fun `transposing by an octave leaves a pitch class untouched`() {
        for (root in StandardRoots) {
            val moved = root.transposeBy(SpelledInterval.PERFECT_OCTAVE)
            val spelled = assertIs<SpellingResult.Spelled<SpelledPitchClass>>(moved)
            assertEquals(root, spelled.value)
        }
    }

    @Test
    fun `a slash bass travels with the chord`() {
        val overE = JazzChordParser.parseOrThrow("C/E")
        val moved = Transposition.transpose(overE, SpelledInterval.MAJOR_SECOND)
        val spelled = assertIs<SpellingResult.Spelled<ChordSpec>>(moved)
        assertEquals("D", spelled.value.root.toString())
        assertEquals("F#", spelled.value.explicitBass.toString())
    }

    @Test
    fun `transposing a voicing preserves its shape and every voice's role`() {
        val original = realizer.analyze(
            JazzChordParser.parseOrThrow("Cmaj7"),
            listOf(60, 64, 67, 71).map { MidiNote(it) },
        )
        val moved = Transposition.transpose(original, SpelledInterval.MINOR_THIRD)
        val voicing = assertNotNull(assertIs<SpellingResult.Spelled<Voicing?>>(moved).value)

        assertEquals(listOf(63, 67, 70, 74), voicing.pitches.map { it.value })
        assertEquals("Ebmaj7", voicing.chord.symbol)
        assertEquals(original.metadata.degreesByVoice, voicing.metadata.degreesByVoice)
        assertEquals(original.metadata.spanSemitones, voicing.metadata.spanSemitones)
    }

    @Test
    fun `diatonic transposition stays in the key`() {
        val key = KeyContext.major("C")
        val twoChord = JazzChordParser.parseOrThrow("Dm7")
        val upOne = Transposition.transposeDiatonically(twoChord, 1, key)
        assertEquals("E", assertIs<SpellingResult.Spelled<ChordSpec>>(upOne).value.root.toString())

        val flatKey = KeyContext.major("Eb")
        val fMinor = JazzChordParser.parseOrThrow("Fm7")
        val upTwo = Transposition.transposeDiatonically(fMinor, 2, flatKey)
        assertEquals("Ab", assertIs<SpellingResult.Spelled<ChordSpec>>(upTwo).value.root.toString())
    }

    @Test
    fun `randomised round trips hold across every root, formula and interval`() {
        val random = DefaultSeededRandomFactory.create(PROPERTY_SEED)
        val formulas = ChordFormulas.all

        repeat(PROPERTY_ITERATIONS) { iteration ->
            val chord = ChordSpec(random.pick(StandardRoots), random.pick(formulas).id)
            val interval = random.pick(intervals)
            val direction = if (random.nextBoolean()) interval else -interval

            val moved = Transposition.transpose(chord, direction)
            if (moved !is SpellingResult.Spelled) return@repeat
            val back = Transposition.transpose(moved.value, -direction)
            val restored = assertIs<SpellingResult.Spelled<ChordSpec>>(back)

            assertEquals(
                chord,
                restored.value,
                "seed=$PROPERTY_SEED iteration=$iteration chord=${chord.symbol} interval=$direction",
            )
        }
    }

    @Test
    fun `randomised transposition never changes how many tones a chord has`() {
        val random = DefaultSeededRandomFactory.create(PROPERTY_SEED + 1)

        repeat(PROPERTY_ITERATIONS) { iteration ->
            val chord = ChordSpec(random.pick(StandardRoots), random.pick(ChordFormulas.all).id)
            val interval = random.pick(intervals)
            val moved = Transposition.transpose(chord, interval)
            if (moved !is SpellingResult.Spelled) return@repeat

            // Some transpositions land on a root that standard notation cannot spell the chord
            // from at all — see the Cb diminished seventh case below. Those are reported, not
            // silently respelled, so the property only applies when both sides are writable.
            val transposedTones = realizer.trySpell(moved.value)
            if (transposedTones !is SpellingResult.Spelled) return@repeat

            assertEquals(
                realizer.chordTones(chord).size,
                transposedTones.value.size,
                "seed=${PROPERTY_SEED + 1} iteration=$iteration chord=${chord.symbol}",
            )
        }
    }

    @Test
    fun `a chord that standard notation cannot write is reported rather than respelled`() {
        // A Cb diminished seventh needs a B triple-flat for its diminished seventh. There is
        // no such note in standard notation, so the engine refuses instead of quietly handing
        // back an A — which would be the same sound but a different chord on the page.
        // Content must choose B diminished seventh instead; the validator added in Phase 6
        // will enforce that for authored curriculum.
        val unwritable = ChordSpec(
            root = requireNotNull(SpelledPitchClass.parseOrNull("Cb")),
            formulaId = ChordFormulas.DiminishedSeventh.id,
        )

        val result = realizer.trySpell(unwritable)
        val overflow = assertIs<SpellingResult.Overflow>(result)
        assertEquals(-3, overflow.requiredOffset)

        assertTrue(
            realizer.generateVoicings(unwritable, VoicingPolicy(requiredDegrees = emptySet())).isEmpty(),
            "An unwritable chord must produce no voicings rather than a half-correct one",
        )
    }

    @Test
    fun `every standard root reaches every other standard root`() {
        val cMajorSeventh = JazzChordParser.parseOrThrow("Cmaj7")
        val transposed = Transposition.toAllRoots(cMajorSeventh, StandardRoots)
        assertEquals(12, transposed.size)
        assertEquals(
            (0..11).toSet(),
            transposed.map { it.root.pitchClass.value }.toSet(),
            "All twelve pitch classes must be reachable",
        )
        assertTrue(transposed.all { it.formulaId == cMajorSeventh.formulaId })
    }

    private companion object {
        const val PROPERTY_SEED = 20260815L
        const val PROPERTY_ITERATIONS = 500
    }
}
