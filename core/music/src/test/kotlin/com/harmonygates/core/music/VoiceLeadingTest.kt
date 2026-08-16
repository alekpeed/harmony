package com.harmonygates.core.music

import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voiceleading.MotionType
import com.harmonygates.core.music.voiceleading.VoiceLeadingAnalyzer
import com.harmonygates.core.music.voiceleading.VoiceLeadingWeights
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Voice-leading metrics (04_HARMONY_DOMAIN_ENGINE.md §11).
 *
 * The fixtures below are chord changes whose smoothest reading is known by ear, which is what
 * makes them useful: an analyzer that paired voices by index would report a plausible-looking
 * but musically wrong answer for several of them.
 */
class VoiceLeadingTest {

    private val realizer = DefaultChordRealizer()
    private val analyzer = VoiceLeadingAnalyzer()

    private fun notes(vararg values: Int) = values.map { MidiNote(it) }

    @Test
    fun `a held chord shows no motion and all common tones`() {
        val chord = notes(60, 64, 67, 71)
        val analysis = analyzer.analyze(chord, chord)

        assertEquals(0, analysis.totalMotionSemitones)
        assertEquals(0, analysis.maxLeapSemitones)
        assertEquals(4, analysis.commonToneCount)
        assertEquals(4, analysis.pitchClassCommonToneCount)
        assertEquals(0, analysis.voiceCrossings)
    }

    @Test
    fun `Dm7 to G7 in close position moves by the smallest available distance`() {
        // D F A C -> D F G B: the smoothest reading holds D and F, and moves A->G, C->B.
        val analysis = analyzer.analyze(notes(62, 65, 69, 72), notes(62, 65, 67, 71))

        assertEquals(3, analysis.totalMotionSemitones, "Two voices move a step each; one moves a semitone")
        assertEquals(2, analysis.commonToneCount)
        assertEquals(2, analysis.maxLeapSemitones)
    }

    @Test
    fun `guide tones resolve by half step through a two-five-one`() {
        // The 3rd and 7th of Dm7 (F, C) resolving into G7 (F, B).
        val analysis = analyzer.analyze(notes(65, 72), notes(65, 71))

        assertEquals(1, analysis.totalMotionSemitones)
        assertEquals(1, analysis.commonToneCount, "F is common to both chords")
        assertEquals(1, analysis.maxLeapSemitones)
    }

    @Test
    fun `voices are paired optimally rather than by position`() {
        // C4 B4 -> B3 C4 E4. Pairing by index reads this as C4->B3 and B4->C4, eleven
        // semitones of movement with a new E4 appearing on top. The musical reading is that
        // C4 stays put, B4 falls to E4, and the B3 underneath is the new voice: seven
        // semitones. Only an assignment search finds the second one.
        val analysis = analyzer.analyze(notes(60, 71), notes(59, 60, 64))

        assertEquals(7, analysis.totalMotionSemitones)
        assertEquals(1, analysis.commonToneCount, "C4 is held")
        assertEquals(listOf(MidiNote(59)), analysis.addedVoices)
        assertTrue(analysis.droppedVoices.isEmpty())
    }

    @Test
    fun `an added voice is reported as added rather than as a huge leap`() {
        val analysis = analyzer.analyze(notes(60, 64, 67), notes(60, 64, 67, 71))

        assertEquals(1, analysis.addedVoices.size)
        assertEquals(MidiNote(71), analysis.addedVoices.single())
        assertTrue(analysis.droppedVoices.isEmpty())
        assertEquals(0, analysis.totalMotionSemitones, "The three shared voices did not move")
    }

    @Test
    fun `a dropped voice is reported as dropped`() {
        val analysis = analyzer.analyze(notes(60, 64, 67, 71), notes(60, 64, 67))

        assertEquals(1, analysis.droppedVoices.size)
        assertEquals(MidiNote(71), analysis.droppedVoices.single())
        assertTrue(analysis.addedVoices.isEmpty())
    }

    @Test
    fun `add and drop penalties are configurable`() {
        val cheap = VoiceLeadingAnalyzer(VoiceLeadingWeights(addPenalty = 0.5))
        val expensive = VoiceLeadingAnalyzer(VoiceLeadingWeights(addPenalty = 50.0))

        val from = notes(60, 64, 67)
        val to = notes(60, 64, 67, 71)

        assertTrue(
            cheap.analyze(from, to).cost < expensive.analyze(from, to).cost,
            "A policy that tolerates a new voice should score the change more kindly",
        )
    }

    @Test
    fun `motion types are classified pairwise`() {
        // One voice up a tone, one voice down a tone: contrary motion.
        val contrary = analyzer.analyze(notes(60, 72), notes(62, 70))
        assertEquals(1, contrary.contraryMotionCount)
        assertEquals(0, contrary.similarMotionCount)

        // Both voices up two semitones: parallel.
        val parallel = analyzer.analyze(notes(60, 67), notes(62, 69))
        assertEquals(1, parallel.parallelMotionCount)

        // Both up, by different amounts: similar.
        val similar = analyzer.analyze(notes(60, 67), notes(62, 71))
        assertEquals(1, similar.similarMotionCount)

        // One holds while the other moves: oblique.
        val oblique = analyzer.analyze(notes(60, 67), notes(60, 69))
        assertEquals(1, oblique.obliqueMotionCount)

        assertEquals(1, analyzer.analyze(notes(60, 67), notes(60, 67)).motionTypeCounts[MotionType.STATIONARY])
    }

    @Test
    fun `analysis is deterministic`() {
        val from = realizer.analyze(JazzChordParser.parseOrThrow("Dm7"), notes(50, 60, 65, 69))
        val to = realizer.analyze(JazzChordParser.parseOrThrow("G7"), notes(43, 59, 65, 67))

        val first = analyzer.analyze(from, to)
        val second = analyzer.analyze(from, to)
        assertEquals(first, second)
    }

    @Test
    fun `a smoother change scores lower than a clumsier one`() {
        val dMinorSeventh = notes(62, 65, 69, 72)
        val smoothG7 = notes(62, 65, 67, 71)
        val clumsyG7 = notes(43, 47, 50, 53)

        assertTrue(
            analyzer.analyze(dMinorSeventh, smoothG7).cost < analyzer.analyze(dMinorSeventh, clumsyG7).cost,
            "Stepwise voice leading must score better than dropping the whole hand two octaves",
        )
    }

    @Test
    fun `every voice on both sides is accounted for exactly once`() {
        val from = notes(48, 55, 64, 71)
        val to = notes(50, 53, 59, 65, 69)

        val analysis = analyzer.analyze(from, to)
        val touchedSources = analysis.motions.map { it.from } + analysis.droppedVoices
        val touchedTargets = analysis.motions.map { it.to } + analysis.addedVoices

        assertEquals(from.sorted(), touchedSources.sorted())
        assertEquals(to.sorted(), touchedTargets.sorted())
    }
}
