package com.harmonygates.core.music

import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.performance.DefaultPerformanceEvaluator
import com.harmonygates.core.music.performance.PerformanceError
import com.harmonygates.core.music.performance.Verdict
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.pitch.SpellingResult
import com.harmonygates.core.music.progression.ChordEvent
import com.harmonygates.core.music.progression.DefaultProgressionGenerator
import com.harmonygates.core.music.progression.KeyOrder
import com.harmonygates.core.music.progression.Progression
import com.harmonygates.core.music.progression.ProgressionTemplates
import com.harmonygates.core.music.progression.VoicingStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The progression domain: templates placed in keys, and the window the track draws.
 *
 * 03_JAZZ_CURRICULUM.md Region 12. The point of writing progressions as functions is that a
 * `ii-V-I` exists once and is placed twelve times, so the tests check the placement rather than
 * re-listing the chords.
 */
class ProgressionTest {

    private val generator = DefaultProgressionGenerator()
    private val evaluator = DefaultPerformanceEvaluator()

    private fun key(name: String) = KeyContext(requireNotNull(SpelledPitchClass.parseOrNull(name)))

    private fun generate(
        template: com.harmonygates.core.music.progression.ProgressionTemplate,
        keyName: String,
        style: VoicingStyle = VoicingStyle.ANY_VOICING,
    ): Progression = assertIs<SpellingResult.Spelled<Progression>>(
        generator.generate(template, key(keyName), style),
    ).value

    private fun verdict(event: ChordEvent, notes: List<Int>): Verdict =
        evaluator.evaluate(event.requirement, attemptOf(notes)).verdict

    // --- Placing templates --------------------------------------------------------------------

    @Test
    fun `a major two five one lands on the right chords`() {
        val progression = generate(ProgressionTemplates.MajorTwoFiveOne, "C")
        assertEquals(listOf("Dm7", "G7", "Cmaj7"), progression.events.map { it.displaySymbol })
    }

    @Test
    fun `a two five one in Eb is spelled with flats`() {
        val progression = generate(ProgressionTemplates.MajorTwoFiveOne, "Eb")
        assertEquals(listOf("Fm7", "Bb7", "Ebmaj7"), progression.events.map { it.displaySymbol })
    }

    @Test
    fun `each chord keeps its roman numeral`() {
        val progression = generate(ProgressionTemplates.MajorTwoFiveOne, "F")
        assertEquals(listOf("ii7", "V7", "Imaj7"), progression.events.map { it.functionLabel })
    }

    @Test
    fun `the tritone substitution keeps its flat two`() {
        val progression = generate(ProgressionTemplates.TritoneSubTurnaround, "C")
        assertEquals(listOf("Dm7", "Db7", "Cmaj7"), progression.events.map { it.displaySymbol })
    }

    @Test
    fun `a run through all keys is one long progression, not twelve short ones`() {
        val progression = generator.throughAllKeys(ProgressionTemplates.MajorTwoFiveOne)
        assertEquals(
            DefaultProgressionGenerator.CYCLE_OF_FOURTHS.size * 3,
            progression.size,
            "Twelve keys of ii-V-I is thirty-six chords in one run",
        )
        assertEquals(
            progression.size,
            progression.events.map { it.id }.distinct().size,
            "Every event needs its own id so the renderer can key on it",
        )
    }

    @Test
    fun `a seeded key order reproduces exactly`() {
        val first = generator.throughAllKeys(
            ProgressionTemplates.MajorTwoFiveOne,
            order = KeyOrder.SEEDED_SHUFFLE,
            seed = 99,
        )
        val second = generator.throughAllKeys(
            ProgressionTemplates.MajorTwoFiveOne,
            order = KeyOrder.SEEDED_SHUFFLE,
            seed = 99,
        )
        assertEquals(first.events.map { it.displaySymbol }, second.events.map { it.displaySymbol })
    }

    // --- What the track shows ------------------------------------------------------------------

    @Test
    fun `the window is one chord of history and six upcoming`() {
        val progression = generator.throughAllKeys(ProgressionTemplates.MajorTwoFiveOne)
        val window = progression.window(activeIndex = 10)

        assertEquals(8, window.size, "Eight perspective slots is the landscape composition")
        assertEquals(-1..6, window.first().relativeSlot..window.last().relativeSlot)
        assertEquals(0, window.first { it.eventIndex == 10 }.relativeSlot, "The active chord is slot 0")
    }

    @Test
    fun `the window is shorter at the start and the end rather than inventing chords`() {
        val progression = generate(ProgressionTemplates.MajorTwoFiveOne, "C")
        assertEquals(3, progression.window(activeIndex = 0).size, "A three-chord run shows three orbs")
        assertEquals(
            listOf(0, 1, 2),
            progression.window(activeIndex = 0).map { it.eventIndex },
        )
    }

    @Test
    fun `a looping run keeps the track full and gives each pass its own identity`() {
        val progression = generate(ProgressionTemplates.Turnaround, "C")
        val window = progression.window(activeIndex = 2)

        assertEquals(8, window.size, "A looping run never runs out of upcoming chords")
        val firstChordAppearances = window.filter { it.event.id == progression.events.first().id }
        assertTrue(firstChordAppearances.size >= 2, "A four-chord loop shows the first chord twice")
        assertNotEquals(
            firstChordAppearances[0].instanceId,
            firstChordAppearances[1].instanceId,
            "Two passes of the same chord must be two orbs, not one orb in two places",
        )
    }

    // --- Acceptance: several renderings are correct where the policy allows ---------------------

    @Test
    fun `an any-voicing run accepts inversions, omissions and doublings of the same chord`() {
        val dm7 = generate(ProgressionTemplates.MajorTwoFiveOne, "C").events.first()

        val renderings = mapOf(
            "root position" to listOf(50, 53, 57, 60),
            "first inversion" to listOf(53, 57, 60, 62),
            "seventh in the bass" to listOf(48, 53, 57, 62),
            "no fifth" to listOf(50, 53, 60),
            "doubled root, spread over two hands" to listOf(38, 50, 53, 57, 60),
        )

        renderings.forEach { (description, notes) ->
            assertTrue(
                verdict(dm7, notes).isCorrect,
                "A Dm7 played as $description is a Dm7: ${verdict(dm7, notes)}",
            )
        }
    }

    @Test
    fun `a root-position run diagnoses an inversion as a wrong bass`() {
        val dm7 = generate(ProgressionTemplates.MajorTwoFiveOne, "C", VoicingStyle.ROOT_POSITION).events.first()
        val result = evaluator.evaluate(dm7.requirement, attemptOf(listOf(53, 57, 60, 62)))

        assertEquals(Verdict.PARTIAL, result.verdict, "The notes are right and the bass is not")
        val bass = result.semanticErrors.filterIsInstance<PerformanceError.WrongBass>().firstOrNull()
        assertEquals("D", bass?.expected?.toString(), "Root position wants the D underneath: $result")
    }

    @Test
    fun `a shell run judges the shape, not just the notes`() {
        val g7 = generate(ProgressionTemplates.MajorTwoFiveOne, "C", VoicingStyle.SHELL).events[1]

        assertTrue(verdict(g7, listOf(43, 47, 53)).isCorrect, "G2 B2 F3 is the shell")
        assertEquals(
            Verdict.PARTIAL,
            verdict(g7, listOf(43, 47, 50, 53)),
            "Adding the fifth makes it a different voicing",
        )
    }

    @Test
    fun `a rootless run is judged against the tensions the shape sounds`() {
        val g7 = generate(ProgressionTemplates.MajorTwoFiveOne, "C", VoicingStyle.ROOTLESS_A).events[1]

        // The event still shows G7; correctness is asked of the extended chord behind it.
        assertEquals("G7", g7.displaySymbol)
        assertTrue(verdict(g7, listOf(59, 64, 65, 69)).isCorrect, "B3 E4 F4 A4 is rootless A on G7")
    }
}
