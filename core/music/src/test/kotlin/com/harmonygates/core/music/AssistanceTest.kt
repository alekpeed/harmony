package com.harmonygates.core.music

import com.harmonygates.core.music.assistance.AssistanceChannel
import com.harmonygates.core.music.assistance.AssistanceLevel
import com.harmonygates.core.music.assistance.AssistanceProfile
import com.harmonygates.core.music.assistance.DifficultyPreset
import com.harmonygates.core.music.assistance.HintLadder
import com.harmonygates.core.music.assistance.Recovery
import com.harmonygates.core.music.assistance.RecoveryPolicy
import com.harmonygates.core.music.chord.ChordFormulas
import com.harmonygates.core.music.exercise.DefaultExerciseGenerator
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.ExercisePolicyId
import com.harmonygates.core.music.exercise.SkillId
import com.harmonygates.core.music.mastery.ErrorClass
import com.harmonygates.core.music.session.ExercisePresentationModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The assistance system.
 *
 * 15_IMPLEMENTATION_PHASES.md gives Phase 7 one acceptance criterion and it is the important
 * one: "same exercise can be presented at multiple assistance levels without changing answer
 * logic". Assistance decides what the player is shown, never what counts as right.
 */
class AssistanceTest {

    private val generator = DefaultExerciseGenerator()

    private fun policy(presentation: com.harmonygates.core.music.exercise.PresentationSpec) = ExercisePolicy(
        id = ExercisePolicyId("policy.test"),
        skillIds = setOf(SkillId("skill.test")),
        formulaPool = listOf(ChordFormulas.DominantSeventh.id, ChordFormulas.MinorSeventh.id),
        presentation = presentation,
        sessionLength = 8,
    )

    // --- Acceptance ---------------------------------------------------------------------------

    @Test
    fun `the same exercise at every assistance level asks the same question`() {
        val requirements = AssistanceLevel.entries.map { level ->
            generator.generate(policy(level.profile.presentation), seed = 99).requirement
        }

        assertEquals(
            1,
            requirements.distinct().size,
            "Eight assistance levels produced ${requirements.distinct().size} different answers",
        )
    }

    @Test
    fun `the same exercise at every assistance level is the same chord`() {
        val chords = AssistanceLevel.entries.map { level ->
            generator.generate(policy(level.profile.presentation), seed = 7).chord
        }

        assertEquals(1, chords.distinct().size, "Assistance changed the material: $chords")
    }

    @Test
    fun `assistance changes only what is shown`() {
        val guided = generator.generate(policy(AssistanceLevel.A1.profile.presentation), seed = 5)
        val blind = generator.generate(policy(AssistanceLevel.A7.profile.presentation), seed = 5)

        assertEquals(guided.requirement, blind.requirement)
        assertEquals(guided.chord, blind.chord)
        assertEquals(guided.spelledTones, blind.spelledTones, "The tones exist either way")

        val guidedModel = ExercisePresentationModel.of(guided, 0, 8)
        val blindModel = ExercisePresentationModel.of(blind, 0, 8)
        assertTrue(guidedModel.spelledNoteNames.isNotEmpty(), "A1 shows the notes")
        assertTrue(blindModel.spelledNoteNames.isEmpty(), "A7 does not")
        assertNull(blindModel.chordSymbol, "A7 is audio only")
    }

    // --- Profiles and levels -------------------------------------------------------------------

    @Test
    fun `a profile round-trips through a presentation spec`() {
        AssistanceLevel.entries.forEach { level ->
            val roundTripped = AssistanceProfile.from(level.profile.presentation)
            // Fingering, audio, metronome and context have no presentation switch yet, so they
            // are dropped by the round trip. Everything with a switch must survive it.
            val expected = level.profile.channels.filter { it.hasPresentationSwitch() }.toSet()
            assertEquals(expected, roundTripped.channels, "$level did not survive the round trip")
        }
    }

    private fun AssistanceChannel.hasPresentationSwitch(): Boolean = this !in setOf(
        AssistanceChannel.FINGERING,
        AssistanceChannel.REFERENCE_AUDIO,
        AssistanceChannel.METRONOME,
        AssistanceChannel.CONTEXT_CHORDS,
    )

    @Test
    fun `the levels run from most help to least`() {
        val revealing = AssistanceLevel.entries.map { level ->
            level.profile.channels.count { it.revealsTheAnswer }
        }

        assertTrue(revealing.first() > revealing.last(), "A0 should help more than A7: $revealing")
        assertTrue(AssistanceLevel.A3.profile.isIndependent, "A3 is symbol only, which is the question")
        assertFalse(AssistanceLevel.A1.profile.isIndependent, "A1 shows the notes")
    }

    @Test
    fun `a chord symbol is the question, note names are the answer`() {
        assertFalse(AssistanceChannel.CHORD_SYMBOL.revealsTheAnswer)
        assertFalse(AssistanceChannel.ROMAN_NUMERAL.revealsTheAnswer)
        assertTrue(AssistanceChannel.NOTE_NAMES.revealsTheAnswer)
        assertTrue(AssistanceChannel.KEYBOARD_TARGETS.revealsTheAnswer)
    }

    @Test
    fun `every difficulty preset resolves to a level`() {
        DifficultyPreset.entries.forEach { preset ->
            assertEquals(preset.level.profile, preset.profile, "${preset.label} lost its profile")
        }
        assertEquals(AssistanceLevel.A0, DifficultyPreset.LEARN.level)
        assertEquals(AssistanceLevel.A7, DifficultyPreset.BLIND.level)
    }

    // --- The hint ladder -------------------------------------------------------------------------

    @Test
    fun `a hint reveals one thing at a time`() {
        val ladder = HintLadder()
        var profile = AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL)

        val first = assertNotNull(ladder.next(profile))
        assertEquals(profile.channels.size + 1, first.profile.channels.size, "One channel, not several")

        profile = first.profile
        val second = assertNotNull(ladder.next(profile))
        assertTrue(second.channel != first.channel, "The same hint twice is not a ladder")
    }

    @Test
    fun `hints run out rather than repeating`() {
        val ladder = HintLadder()
        var profile = AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL)
        val revealed = mutableListOf<AssistanceChannel>()

        while (true) {
            val hint = ladder.next(profile) ?: break
            revealed += hint.channel
            profile = hint.profile
        }

        assertEquals(revealed.size, revealed.distinct().size, "A channel was revealed twice")
        assertEquals(0, ladder.remaining(profile))
        assertNull(ladder.next(profile), "An exhausted ladder offers nothing, not the same hint again")
    }

    @Test
    fun `structure is offered before the notes themselves`() {
        val ladder = HintLadder()
        val order = mutableListOf<AssistanceChannel>()
        var profile = AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL)
        while (true) {
            val hint = ladder.next(profile) ?: break
            order += hint.channel
            profile = hint.profile
        }

        assertTrue(
            order.indexOf(AssistanceChannel.INVERSION_LABEL) < order.indexOf(AssistanceChannel.NOTE_NAMES),
            "Knowing the inversion is a smaller gift than being shown the notes: $order",
        )
        assertTrue(
            order.indexOf(AssistanceChannel.NOTE_NAMES) < order.indexOf(AssistanceChannel.KEYBOARD_TARGETS),
            "Naming the notes comes before pointing at the keys: $order",
        )
    }

    @Test
    fun `a hint targets the mistake that was actually made`() {
        val ladder = HintLadder()
        val profile = AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL)

        assertEquals(
            AssistanceChannel.TARGET_BASS,
            assertNotNull(ladder.forError(profile, ErrorClass.WRONG_BASS)).channel,
            "A wrong bass calls for the bass, not the metronome",
        )
        assertEquals(
            AssistanceChannel.NOTE_NAMES,
            assertNotNull(ladder.forError(profile, ErrorClass.MISSING_CHORD_TONE)).channel,
        )
        assertEquals(
            AssistanceChannel.METRONOME,
            assertNotNull(ladder.forError(profile, ErrorClass.RHYTHM)).channel,
        )
    }

    @Test
    fun `a targeted hint already showing falls back to the ordinary ladder`() {
        val ladder = HintLadder()
        val profile = AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL, AssistanceChannel.TARGET_BASS)
        val hint = assertNotNull(ladder.forError(profile, ErrorClass.WRONG_BASS))

        assertTrue(hint.channel != AssistanceChannel.TARGET_BASS, "It is already on screen")
    }

    // --- The recovery loop -----------------------------------------------------------------------

    @Test
    fun `one wrong answer is not a pattern`() {
        val decision = RecoveryPolicy().decide(
            listOf(ErrorClass.MISSING_CHORD_TONE),
            AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL),
        )

        assertEquals(Recovery.CarryOn, decision)
    }

    @Test
    fun `two of the same mistake offers a scaffolded retry`() {
        val decision = RecoveryPolicy().decide(
            List(2) { ErrorClass.MISSING_CHORD_TONE },
            AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL),
        )

        val scaffold = assertIs<Recovery.Scaffold>(decision)
        assertEquals(AssistanceChannel.NOTE_NAMES, scaffold.hint.channel)
        assertTrue(scaffold.explanation.isNotBlank())
    }

    @Test
    fun `three of the same mistake isolates the failing component`() {
        val decision = RecoveryPolicy().decide(
            List(3) { ErrorClass.WRONG_BASS },
            AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL),
        )

        val isolate = assertIs<Recovery.Isolate>(decision)
        assertEquals(ErrorClass.WRONG_BASS, isolate.errorClass)
    }

    @Test
    fun `three different mistakes are not one thing to isolate`() {
        val decision = RecoveryPolicy().decide(
            listOf(ErrorClass.WRONG_BASS, ErrorClass.ONSET_TIMING, ErrorClass.MISSING_CHORD_TONE),
            AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL),
        )

        assertTrue(
            decision is Recovery.Scaffold,
            "A player making a different mistake each time is exploring, not stuck: $decision",
        )
    }

    @Test
    fun `an explanation describes this exercise, not a law of music`() {
        val decision = RecoveryPolicy().decide(
            List(2) { ErrorClass.EXTRA_NOTE },
            AssistanceProfile.of(AssistanceChannel.CHORD_SYMBOL),
        )
        val scaffold = assertIs<Recovery.Scaffold>(decision)

        // 21_CONTENT_AUTHORING_GUIDE.md §7's example of what not to say is "Never play the fifth
        // in a dominant chord". Nothing here should read like a universal rule.
        assertFalse(scaffold.explanation.contains("Never", ignoreCase = true), scaffold.explanation)
        assertFalse(scaffold.explanation.contains("always", ignoreCase = true), scaffold.explanation)
        assertTrue(scaffold.explanation.contains("this voicing"), scaffold.explanation)
    }
}
