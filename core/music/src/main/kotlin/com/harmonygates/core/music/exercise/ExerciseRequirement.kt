package com.harmonygates.core.music.exercise

import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.pitch.MidiNote
import com.harmonygates.core.music.pitch.SpelledPitchClass
import com.harmonygates.core.music.voicing.Voicing
import com.harmonygates.core.music.voicing.VoicingPolicy

/**
 * What a performance has to satisfy.
 *
 * 04_HARMONY_DOMAIN_ENGINE.md §12: every game mode — chord gates, ear training, sight reading,
 * voice-leading challenges — converges on this one type, so there is exactly one place that
 * decides whether an answer is right. Phase 3 adds the evaluator that consumes these; Phase 1
 * defines the language they are written in.
 */
public sealed interface ExerciseRequirement {

    /**
     * "Play any C major seventh." Octave and voicing are free.
     *
     * @param pitchClasses the tones that must sound, spelled so feedback can name them.
     * @param allowExtraNotes whether notes outside the set invalidate the answer.
     * @param allowOctaveDoubling whether the same tone may appear twice.
     * @param requiredBass a bass constraint when the exercise is about inversion.
     */
    public data class PitchSet(
        val pitchClasses: Set<SpelledPitchClass>,
        val allowExtraNotes: Boolean = false,
        val allowOctaveDoubling: Boolean = true,
        val requiredBass: SpelledPitchClass? = null,
        val chord: ChordSpec? = null,
    ) : ExerciseRequirement

    /**
     * "Play exactly this." Register, spacing and doubling all count.
     *
     * @param octaveEquivalent when true the shape may be played in any octave as a whole, but
     *   its internal spacing must be preserved.
     */
    public data class ExactVoicing(
        val voicing: Voicing,
        val octaveEquivalent: Boolean = false,
    ) : ExerciseRequirement

    /**
     * "Play a G13 that fits these rules." The policy decides what counts.
     *
     * This is the requirement rootless and shell voicings use, where several different answers
     * are all correct (03_JAZZ_CURRICULUM.md §9).
     */
    public data class ChordPolicyMatch(
        val chord: ChordSpec,
        val policy: VoicingPolicy,
    ) : ExerciseRequirement

    /** "Play these chords, in order, in time." */
    public data class TimedSequence(
        val steps: List<SequenceStep>,
        val tempoBpm: Int,
        val beatsPerStep: Int = 1,
    ) : ExerciseRequirement {
        init {
            require(steps.isNotEmpty()) { "A timed sequence needs at least one step" }
            require(tempoBpm > 0) { "Tempo must be positive: $tempoBpm" }
        }
    }

    /**
     * "Get from this voicing to that harmony, smoothly."
     *
     * Several answers may score highly, so the requirement carries constraints rather than a
     * single expected voicing (03_JAZZ_CURRICULUM.md §15).
     */
    public data class VoiceLeadingTarget(
        val startingVoicing: Voicing,
        val destination: ChordSpec,
        val policy: VoicingPolicy,
        val maximumTotalMotionSemitones: Int? = null,
        val maximumLeapSemitones: Int? = null,
        val requiredTopNote: MidiNote? = null,
    ) : ExerciseRequirement

    /**
     * "Read and play this phrase."
     *
     * Phase 9 owns the notation domain; the requirement references a phrase by id so that
     * `core:music` does not have to model beams and ties before sight reading exists.
     */
    public data class SightReadingPhrase(
        val phraseId: String,
        val expectedNotes: List<MidiNote>,
        val gradeRhythm: Boolean,
    ) : ExerciseRequirement
}

/** One chord in a [ExerciseRequirement.TimedSequence]. */
public data class SequenceStep(
    val requirement: ExerciseRequirement,
    val beat: Int,
) {
    init {
        require(beat >= 0) { "Beat index must be non-negative: $beat" }
    }
}
