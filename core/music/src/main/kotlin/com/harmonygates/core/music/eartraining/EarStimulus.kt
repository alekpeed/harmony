package com.harmonygates.core.music.eartraining

import com.harmonygates.core.music.chord.ChordSpec
import com.harmonygates.core.music.exercise.ExerciseRequirement
import com.harmonygates.core.music.key.KeyContext
import com.harmonygates.core.music.voicing.Voicing

/** The ear tasks of 07_EAR_TRAINING_ENGINE.md §2. */
public enum class EarTaskFamily(public val label: String) {
    /** The app plays a chord; the player reproduces it on the keyboard. */
    REPRODUCE("Reproduce"),

    /** Name it, then play it — so a lucky guess is not mistaken for mastery. */
    IDENTIFY_THEN_PLAY("Identify, then play"),

    /** A then B: what changed? */
    DIFFERENCE_DETECTION("What changed"),

    /** Given a key, hear and reproduce a function rather than a letter. */
    FUNCTION_HEARING("Function"),

    /** Upper structure over a moving bass. */
    BASS_HEARING("Bass line"),

    /** Two voicings: which voice moved? */
    VOICE_LEADING_HEARING("Voice leading"),
}

/** How much replaying a gate allows (07 §5). */
public sealed interface ReplayRule {
    public data object Unlimited : ReplayRule

    public data class Limited(val plays: Int) : ReplayRule {
        init {
            require(plays > 0) { "A limit of zero plays is not an ear exercise" }
        }
    }

    /** One hearing. The Challenge preset. */
    public data object Once : ReplayRule

    public fun permits(playsSoFar: Int): Boolean = when (this) {
        Unlimited -> true
        is Limited -> playsSoFar < plays
        Once -> playsSoFar < 1
    }
}

/** One thing the player hears, as notes and a time. */
public data class StimulusEvent(
    val voicing: Voicing,
    /** Milliseconds from the start of the stimulus. */
    val atMillis: Long,
    val velocities: List<Int>,
) {
    init {
        require(atMillis >= 0) { "A stimulus event cannot happen before the stimulus starts" }
        require(velocities.size == voicing.voiceCount) {
            "Every voice needs a velocity: ${voicing.voiceCount} voices, ${velocities.size} velocities"
        }
    }
}

/**
 * Everything needed to play a stimulus again, exactly.
 *
 * 07_EAR_TRAINING_ENGINE.md §3 lists what a stimulus record must store, and the reason is worth
 * keeping in view: "This makes reported errors reproducible." A player who says "that one
 * sounded wrong" is describing a specific rendering, and without the seed, the voicing, the
 * instrument and the velocities, nobody can hear what they heard.
 */
public data class StimulusSpec(
    val seed: Long,
    val family: EarTaskFamily,
    val chords: List<ChordSpec>,
    val events: List<StimulusEvent>,
    /** The instrument this was rendered with, by id. `core:music` does not depend on the sampler. */
    val instrumentId: String,
    val tempoBpm: Int,
    val key: KeyContext? = null,
    /** Set only when demonstration audio was humanised; §8 requires the seed to be stored. */
    val humanisationSeed: Long? = null,
) {
    init {
        require(chords.isNotEmpty()) { "A stimulus with no chords is silence" }
        require(events.isNotEmpty()) { "A stimulus with no events is silence" }
        require(tempoBpm > 0) { "Tempo must be positive: $tempoBpm" }
    }

    public val durationMillis: Long get() = events.maxOf { it.atMillis }
}

/**
 * An ear-training exercise: what is played, what counts as an answer, and how often it may be
 * heard.
 *
 * The requirement is an ordinary [ExerciseRequirement], which is the whole point of
 * 07 §1: "Ear training must use the same harmonic objects as the chord-construction game. There
 * must not be a separate, inconsistent list of audio chord labels." The answer to an ear
 * exercise is judged by the same evaluator that judges a chord gate, so a `Cmaj7` cannot mean
 * one thing to the eye and another to the ear.
 */
public data class EarExercise(
    val stimulus: StimulusSpec,
    val requirement: ExerciseRequirement,
    val replayRule: ReplayRule = ReplayRule.Unlimited,
    /** Shown before playback, e.g. "Play what you hear." */
    val instruction: String,
    /** For difference detection: what actually changed, once the player has answered. */
    val differenceDescription: String? = null,
) {
    public val family: EarTaskFamily get() = stimulus.family
}

/**
 * How a stimulus was heard, as evidence.
 *
 * 07 §5: "Store replay count as assistance evidence. A correct answer after five replays is
 * still useful practice but should not equal a one-hearing independent response for gate
 * mastery." So the count travels with the answer rather than being discarded once playback ends.
 */
public data class ListeningRecord(
    val plays: Int,
    val heardBeforeAnswering: Boolean,
) {
    /**
     * The assistance grade this listening deserves.
     *
     * One hearing is independent. More than one is help — real help, and worth having, but not
     * the same evidence. Mapped onto the existing evidence ladder rather than a parallel one,
     * because a gate should not have to know whether a skill was proved by eye or by ear.
     */
    public val isIndependentHearing: Boolean get() = plays <= 1
}
