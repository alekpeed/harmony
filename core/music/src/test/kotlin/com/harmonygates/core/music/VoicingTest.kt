package com.harmonygates.core.music

import com.harmonygates.core.music.chord.ChordDegree
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.parse.JazzChordParser
import com.harmonygates.core.music.parse.parseOrThrow
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.realize.DefaultChordRealizer
import com.harmonygates.core.music.voicing.BassRequirement
import com.harmonygates.core.music.voicing.Inversion
import com.harmonygates.core.music.voicing.TopNoteRequirement
import com.harmonygates.core.music.voicing.VoicingFamily
import com.harmonygates.core.music.voicing.VoicingPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Inversion, policy matching and the rootless/omission behaviour Regions 4 and 7 depend on.
 *
 * The load-bearing case is [a rootless voicing is not wrong for missing its root]: a generic
 * "root missing means incorrect" rule would silently fail the whole of Region 7, which
 * 03_JAZZ_CURRICULUM.md §9 calls out explicitly.
 */
class VoicingTest {

    private val realizer = DefaultChordRealizer()

    // C4 = 60.
    private val cMajorSeventh = JazzChordParser.parseOrThrow("Cmaj7")

    @Test
    fun `inversion follows the bass note, not list order`() {
        val cases = mapOf(
            listOf(60, 64, 67, 71) to Inversion.ROOT,
            listOf(64, 67, 71, 72) to Inversion.FIRST,
            listOf(67, 71, 72, 76) to Inversion.SECOND,
            listOf(71, 72, 76, 79) to Inversion.THIRD,
        )

        for ((notes, expected) in cases) {
            val voicing = realizer.analyze(cMajorSeventh, notes.map { MidiNote(it) })
            assertEquals(expected, voicing.metadata.inversion, "Bass ${notes.first()} should give $expected")
        }
    }

    @Test
    fun `an extended chord over an upper tone reports OTHER plus the bass degree`() {
        val thirteenth = JazzChordParser.parseOrThrow("C13")
        // A in the bass: the thirteenth. "Fifth inversion" is not a useful label, so the
        // engine names the bass degree instead.
        val voicing = realizer.analyze(thirteenth, listOf(57, 60, 64, 70, 74).map { MidiNote(it) })
        assertEquals(Inversion.OTHER, voicing.metadata.inversion)
        assertEquals(ChordDegree.THIRTEENTH, voicing.metadata.bassDegree)
    }

    @Test
    fun `a doubled tone is reported rather than discarded`() {
        val voicing = realizer.analyze(cMajorSeventh, listOf(48, 60, 64, 67, 71).map { MidiNote(it) })
        assertEquals(5, voicing.voiceCount, "A doubled root is still two voices")
        assertEquals(setOf(ChordDegree.ROOT), voicing.metadata.doubledDegrees)
    }

    @Test
    fun `a rootless voicing is not wrong for missing its root`() {
        val g13 = JazzChordParser.parseOrThrow("G13")
        val rootlessPolicy = VoicingPolicy(
            // The classic rootless A-position dominant: third, thirteenth, seventh, ninth.
            requiredDegrees = setOf(
                ChordDegree.THIRD,
                ChordDegree.FLAT_SEVENTH,
                ChordDegree.NINTH,
                ChordDegree.THIRTEENTH,
            ),
            optionalDegrees = setOf(ChordDegree.FIFTH),
            requireRoot = false,
            bassRequirement = BassRequirement.DegreeNotInBass(ChordDegree.ROOT),
            pitchRange = 48..84,
            maxVoices = 4,
            namedFamily = VoicingFamily.ROOTLESS_A,
        )

        val voicings = realizer.generateVoicings(g13, rootlessPolicy)
        assertTrue(voicings.isNotEmpty(), "A rootless G13 must be generatable")
        for (voicing in voicings) {
            assertTrue(voicing.metadata.isRootless, "${voicing.spelledPitches} still contains the root")
            assertTrue(
                ChordDegree.THIRD in voicing.metadata.includedDegrees,
                "The third is a guide tone and must survive",
            )
            assertTrue(ChordDegree.FLAT_SEVENTH in voicing.metadata.includedDegrees)
        }
    }

    @Test
    fun `a policy that requires the root rejects rootless candidates`() {
        val g13 = JazzChordParser.parseOrThrow("G13")
        val withRoot = VoicingPolicy(
            requiredDegrees = setOf(ChordDegree.ROOT, ChordDegree.THIRD, ChordDegree.FLAT_SEVENTH),
            requireRoot = true,
            pitchRange = 48..84,
        )
        val voicings = realizer.generateVoicings(g13, withRoot)
        assertTrue(voicings.isNotEmpty())
        assertTrue(voicings.none { it.metadata.isRootless })
    }

    @Test
    fun `an omission is allowed only when the policy says so`() {
        val permissive = VoicingPolicy(
            requiredDegrees = setOf(ChordDegree.ROOT, ChordDegree.THIRD, ChordDegree.SEVENTH),
            optionalDegrees = setOf(ChordDegree.FIFTH),
            allowedOmissions = setOf(ChordDegree.FIFTH),
            pitchRange = 48..84,
        )
        val shells = realizer.generateVoicings(cMajorSeventh, permissive)
        assertTrue(
            shells.any { ChordDegree.FIFTH !in it.metadata.includedDegrees },
            "A 1-3-7 shell should be generatable when the fifth is optional",
        )

        val strict = VoicingPolicy(
            requiredDegrees = setOf(
                ChordDegree.ROOT,
                ChordDegree.THIRD,
                ChordDegree.FIFTH,
                ChordDegree.SEVENTH,
            ),
            pitchRange = 48..84,
        )
        val complete = realizer.generateVoicings(cMajorSeventh, strict)
        assertTrue(complete.isNotEmpty())
        assertTrue(
            complete.all { ChordDegree.FIFTH in it.metadata.includedDegrees },
            "A policy that requires the fifth must not drop it",
        )
    }

    @Test
    fun `a slash bass is enforced and may be a non-chord tone`() {
        val overE = JazzChordParser.parseOrThrow("C/E")
        val inversionVoicings = realizer.generateVoicings(overE, VoicingPolicy(requiredDegrees = emptySet()))
        assertTrue(inversionVoicings.isNotEmpty())
        assertTrue(inversionVoicings.all { it.bass.pitchClass.value == 4 }, "E must be the lowest note")
        assertTrue(inversionVoicings.all { it.metadata.bassDegree == ChordDegree.THIRD })

        val overA = JazzChordParser.parseOrThrow("C/A")
        val slashVoicings = realizer.generateVoicings(overA, VoicingPolicy(requiredDegrees = emptySet()))
        assertTrue(slashVoicings.isNotEmpty())
        assertTrue(slashVoicings.all { it.bass.pitchClass.value == 9 }, "A must be the lowest note")
        assertTrue(
            slashVoicings.all { it.metadata.bassDegree == null },
            "A is not a tone of a C major triad, so it has no chord degree",
        )
        assertTrue(slashVoicings.all { it.metadata.nonChordVoiceCount == 1 })
    }

    @Test
    fun `generated voicings honour the requested register`() {
        val range = 55..72
        val policy = VoicingPolicy(
            requiredDegrees = ChordFormulas.MajorSeventh.requiredDegrees,
            pitchRange = range,
        )
        val voicings = realizer.generateVoicings(cMajorSeventh, policy)
        assertTrue(voicings.isNotEmpty(), "There is room for a Cmaj7 between G3 and C5")
        assertTrue(voicings.all { voicing -> voicing.pitches.all { it.value in range } })
    }

    @Test
    fun `a top-note constraint is respected`() {
        val policy = VoicingPolicy(
            requiredDegrees = ChordFormulas.MajorSeventh.requiredDegrees,
            pitchRange = 48..84,
            topNoteRequirement = TopNoteRequirement.DegreeOnTop(ChordDegree.THIRD),
        )
        val voicings = realizer.generateVoicings(cMajorSeventh, policy)
        assertTrue(voicings.isNotEmpty())
        assertTrue(voicings.all { it.metadata.topDegree == ChordDegree.THIRD })
    }

    @Test
    fun `a max-voice limit is respected`() {
        val policy = VoicingPolicy(
            requiredDegrees = setOf(ChordDegree.ROOT, ChordDegree.THIRD, ChordDegree.SEVENTH),
            optionalDegrees = setOf(ChordDegree.FIFTH),
            maxVoices = 3,
            pitchRange = 48..84,
        )
        val voicings = realizer.generateVoicings(cMajorSeventh, policy)
        assertTrue(voicings.isNotEmpty())
        assertTrue(voicings.all { it.voiceCount <= 3 })
    }

    @Test
    fun `generation is deterministic`() {
        val policy = VoicingPolicy(
            requiredDegrees = ChordFormulas.MajorSeventh.requiredDegrees,
            pitchRange = 48..84,
        )
        val first = realizer.generateVoicings(cMajorSeventh, policy)
        val second = realizer.generateVoicings(cMajorSeventh, policy)
        assertEquals(first, second, "The same chord and policy must produce the same list")
    }

    @Test
    fun `an impossible policy yields nothing rather than a wrong answer`() {
        val policy = VoicingPolicy(
            requiredDegrees = ChordFormulas.MajorThirteenth.requiredDegrees,
            // One semitone of room cannot hold a thirteenth chord.
            pitchRange = 60..61,
        )
        val voicings = realizer.generateVoicings(JazzChordParser.parseOrThrow("Cmaj13"), policy)
        assertTrue(voicings.isEmpty())
    }

    @Test
    fun `a chord tone that is not played is reported as omitted`() {
        val shell = realizer.analyze(cMajorSeventh, listOf(60, 64, 71).map { MidiNote(it) })
        assertEquals(setOf(ChordDegree.FIFTH), shell.metadata.omittedDegrees)
        assertNotNull(shell.metadata.topDegree)
    }

    @Test
    fun `analysis names a foreign note rather than guessing a degree for it`() {
        // F# against a C major seventh: not a chord tone.
        val withForeignNote = realizer.analyze(cMajorSeventh, listOf(60, 64, 66, 67, 71).map { MidiNote(it) })
        val foreign = withForeignNote.tones.single { it.pitch.midiNote.value == 66 }
        assertNull(foreign.degree)
        assertEquals(1, withForeignNote.metadata.nonChordVoiceCount)
    }
}
