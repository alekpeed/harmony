package com.harmonygates.core.music

import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.VoicingFamily
import com.harmonygates.core.music.voicing.VoicingTransforms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Voicing transformations (04_HARMONY_DOMAIN_ENGINE.md §9).
 *
 * Every transformation must preserve the chord's pitch-class content — a drop-2 rearranges a
 * chord, it does not change which chord it is — so that invariant is asserted for each.
 */
class VoicingTransformsTest {

    private val realizer = DefaultChordRealizer()
    private val cMajorSeventh = JazzChordParser.parseOrThrow("Cmaj7")

    /** C4 E4 G4 B4 in close position. */
    private val closeVoicing = realizer.analyze(
        cMajorSeventh,
        listOf(60, 64, 67, 71).map { MidiNote(it) },
        VoicingFamily.CLOSE,
    )

    @Test
    fun `drop 2 moves the second voice from the top down an octave`() {
        val dropped = assertNotNull(VoicingTransforms.drop2(closeVoicing))
        // G4 (67) falls to G3 (55) and becomes the bass.
        assertEquals(listOf(55, 60, 64, 71), dropped.pitches.map { it.value })
        assertEquals(VoicingFamily.DROP_2, dropped.metadata.family)
        assertSamePitchClasses(closeVoicing, dropped)
    }

    @Test
    fun `drop 3 moves the third voice from the top down an octave`() {
        val dropped = assertNotNull(VoicingTransforms.drop3(closeVoicing))
        // E4 (64) falls to E3 (52).
        assertEquals(listOf(52, 60, 67, 71), dropped.pitches.map { it.value })
        assertEquals(VoicingFamily.DROP_3, dropped.metadata.family)
        assertSamePitchClasses(closeVoicing, dropped)
    }

    @Test
    fun `drop 2 and 4 moves both voices down an octave`() {
        val dropped = assertNotNull(VoicingTransforms.drop2And4(closeVoicing))
        // G4 -> G3 and C4 -> C3.
        assertEquals(listOf(48, 55, 64, 71), dropped.pitches.map { it.value })
        assertSamePitchClasses(closeVoicing, dropped)
    }

    @Test
    fun `a triad is too small to drop`() {
        val triad = realizer.analyze(
            JazzChordParser.parseOrThrow("C"),
            listOf(60, 64, 67).map { MidiNote(it) },
        )
        assertNull(VoicingTransforms.drop2(triad), "Drop voicings need four voices")
        assertNull(VoicingTransforms.drop3(triad))
    }

    @Test
    fun `inversion moves the bass up an octave and preserves content`() {
        val first = assertNotNull(VoicingTransforms.invert(closeVoicing))
        assertEquals(listOf(64, 67, 71, 72), first.pitches.map { it.value })
        assertSamePitchClasses(closeVoicing, first)

        val third = assertNotNull(VoicingTransforms.invert(closeVoicing, times = 3))
        assertEquals(listOf(71, 72, 76, 79), third.pitches.map { it.value })
        assertSamePitchClasses(closeVoicing, third)
    }

    @Test
    fun `four inversions of a seventh chord return the original shape an octave up`() {
        val full = assertNotNull(VoicingTransforms.invert(closeVoicing, times = 4))
        assertEquals(closeVoicing.pitches.map { it.value + 12 }, full.pitches.map { it.value })
    }

    @Test
    fun `spread opens the voicing under the upper structure`() {
        val spread = assertNotNull(VoicingTransforms.spread(closeVoicing))
        assertEquals(listOf(48, 64, 67, 71), spread.pitches.map { it.value })
        assertEquals(VoicingFamily.SPREAD, spread.metadata.family)
        assertTrue(spread.metadata.spanSemitones > closeVoicing.metadata.spanSemitones)
    }

    @Test
    fun `close rebuilds a dropped voicing into close position`() {
        val dropped = assertNotNull(VoicingTransforms.drop2(closeVoicing))
        val reclosed = assertNotNull(VoicingTransforms.close(dropped))
        assertEquals(VoicingFamily.CLOSE, reclosed.metadata.family)
        assertTrue(
            reclosed.metadata.spanSemitones < 12,
            "A close-position seventh chord fits inside an octave, got ${reclosed.metadata.spanSemitones}",
        )
        assertSamePitchClasses(closeVoicing, reclosed)
    }

    @Test
    fun `octave displacement moves exactly one voice`() {
        val displaced = assertNotNull(VoicingTransforms.octaveDisplace(closeVoicing, voiceIndex = 3, octaves = 1))
        assertEquals(listOf(60, 64, 67, 83), displaced.pitches.map { it.value })
        assertNull(VoicingTransforms.octaveDisplace(closeVoicing, voiceIndex = 9, octaves = 1))
    }

    @Test
    fun `constraining into range shifts by octaves and never reshapes`() {
        val high = assertNotNull(VoicingTransforms.shiftOctaves(closeVoicing, 2))
        val constrained = assertNotNull(VoicingTransforms.constrainToRange(high, 48..72))
        assertTrue(constrained.pitches.all { it.value in 48..72 })
        assertEquals(
            closeVoicing.pitches.zipWithNext { a, b -> b.value - a.value },
            constrained.pitches.zipWithNext { a, b -> b.value - a.value },
            "Constraining must preserve the internal spacing",
        )
    }

    @Test
    fun `a voicing wider than the range cannot be constrained`() {
        val wide = assertNotNull(VoicingTransforms.spread(closeVoicing))
        assertNull(VoicingTransforms.constrainToRange(wide, 60..64))
    }

    @Test
    fun `bass and top anchoring only accept octave-compatible targets`() {
        val anchored = assertNotNull(VoicingTransforms.withBassAt(closeVoicing, MidiNote(48)))
        assertEquals(48, anchored.bass.value)
        assertEquals(closeVoicing.metadata.spanSemitones, anchored.metadata.spanSemitones)

        assertNull(
            VoicingTransforms.withBassAt(closeVoicing, MidiNote(61)),
            "C# is not an octave of C, so the request has no answer",
        )

        val topAnchored = assertNotNull(VoicingTransforms.withTopAt(closeVoicing, MidiNote(83)))
        assertEquals(83, topAnchored.top.value)
    }

    @Test
    fun `transformations keep every voice's chord role`() {
        val dropped = assertNotNull(VoicingTransforms.drop2(closeVoicing))
        assertEquals(
            closeVoicing.metadata.includedDegrees,
            dropped.metadata.includedDegrees,
            "A drop-2 rearranges a chord; it does not change which chord it is",
        )
    }

    private fun assertSamePitchClasses(
        original: com.harmonygates.core.music.voicing.Voicing,
        transformed: com.harmonygates.core.music.voicing.Voicing,
    ) {
        assertEquals(
            original.pitches.map { it.pitchClass.value }.sorted(),
            transformed.pitches.map { it.pitchClass.value }.sorted(),
            "Transformation changed the chord's pitch content",
        )
    }
}
