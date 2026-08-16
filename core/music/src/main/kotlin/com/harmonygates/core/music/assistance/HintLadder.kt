package com.harmonygates.core.music.assistance

import com.harmonygates.core.music.mastery.ErrorClass

/**
 * One rung of help.
 *
 * @param channel what was revealed.
 * @param profile the assistance in force after revealing it.
 */
public data class Hint(
    val channel: AssistanceChannel,
    val profile: AssistanceProfile,
) {
    public val label: String get() = channel.label
}

/**
 * Reveals help one channel at a time.
 *
 * 02_GAME_LOOP_AND_PROGRESSION.md §6 is specific about the recovery loop: "lower assistance by
 * only one dimension at a time". Handing a stuck player everything at once teaches them that
 * asking is free and tells you nothing about which part they were missing; revealing one thing
 * means the next attempt is evidence about that one thing.
 *
 * The order is a teaching decision, not an arbitrary list. Structural context comes before the
 * notes themselves — knowing the chord is in first inversion is a smaller gift than being shown
 * which keys to press — and the outright answer comes last.
 */
public class HintLadder(
    private val order: List<AssistanceChannel> = DEFAULT_ORDER,
) {

    /**
     * The next channel to reveal, or null when everything this ladder offers is already showing.
     *
     * Returns null rather than looping, so a player who has exhausted the hints is told there
     * are no more instead of being given the same one again.
     */
    public fun next(current: AssistanceProfile): Hint? {
        val channel = order.firstOrNull { it !in current } ?: return null
        return Hint(channel, current.with(channel))
    }

    /** How many hints remain available from here. */
    public fun remaining(current: AssistanceProfile): Int = order.count { it !in current }

    /**
     * Reveals the channel most likely to fix a particular mistake.
     *
     * §6 asks the recovery loop to "isolate the failing component" rather than raise assistance
     * generically. A player who keeps omitting the third does not need the metronome; they need
     * to see which notes the chord is made of. Falls back to the ordinary ladder when the
     * mistake does not suggest anything in particular.
     */
    public fun forError(current: AssistanceProfile, error: ErrorClass): Hint? {
        val targeted = when (error) {
            ErrorClass.WRONG_ROOT, ErrorClass.WRONG_QUALITY, ErrorClass.MISSING_CHORD_TONE,
            ErrorClass.WRONG_ALTERATION, ErrorClass.EXTRA_NOTE,
            -> AssistanceChannel.NOTE_NAMES

            ErrorClass.WRONG_BASS -> AssistanceChannel.TARGET_BASS
            ErrorClass.WRONG_TOP_NOTE -> AssistanceChannel.KEYBOARD_TARGETS
            ErrorClass.REGISTER -> AssistanceChannel.KEYBOARD_TARGETS
            ErrorClass.ONSET_TIMING, ErrorClass.RHYTHM -> AssistanceChannel.METRONOME
            ErrorClass.NOTHING_PLAYED -> AssistanceChannel.REFERENCE_AUDIO
        }
        if (targeted !in current) return Hint(targeted, current.with(targeted))
        return next(current)
    }

    public companion object {
        /** Structure first, then the notes, then the answer outright. */
        public val DEFAULT_ORDER: List<AssistanceChannel> = listOf(
            AssistanceChannel.INVERSION_LABEL,
            AssistanceChannel.VOICING_NAME,
            AssistanceChannel.TARGET_BASS,
            AssistanceChannel.REFERENCE_AUDIO,
            AssistanceChannel.NOTE_NAMES,
            AssistanceChannel.STAFF_NOTATION,
            AssistanceChannel.KEYBOARD_TARGETS,
            AssistanceChannel.FINGERING,
        )
    }
}

/** What the recovery loop decided to do about a run of mistakes. */
public sealed interface Recovery {
    /** Nothing yet: one wrong answer is not a pattern. */
    public data object CarryOn : Recovery

    /** Reveal one more channel and let the player try again. */
    public data class Scaffold(val hint: Hint, val explanation: String) : Recovery

    /**
     * The same mistake keeps happening; practise the missing piece on its own.
     *
     * The gate's `remediation` map names the policy for a given error class. Reaching this is
     * §6's "isolate the failing component", and it is deliberately the last resort rather than
     * the first: being sent to a different exercise is an interruption.
     */
    public data class Isolate(val errorClass: ErrorClass, val explanation: String) : Recovery
}

/**
 * Decides what to do when a player keeps getting the same thing wrong.
 *
 * The whole of 02 §6, which is worth quoting because the order matters: lower assistance by one
 * dimension, show a concise explanation, isolate the failing component, give one scaffolded
 * retry, return to the original task. Failure must not simply restart the same set.
 *
 * @param scaffoldAfter consecutive failures before help is offered unasked.
 * @param isolateAfter consecutive failures with the *same* diagnosis before switching exercise.
 */
public class RecoveryPolicy(
    private val ladder: HintLadder = HintLadder(),
    private val scaffoldAfter: Int = DEFAULT_SCAFFOLD_AFTER,
    private val isolateAfter: Int = DEFAULT_ISOLATE_AFTER,
) {
    init {
        require(scaffoldAfter > 0) { "Scaffolding before the first attempt is not recovery" }
        require(isolateAfter >= scaffoldAfter) { "Isolation should follow scaffolding, not precede it" }
    }

    /**
     * @param recentErrors the diagnoses of consecutive failed attempts on this exercise, newest
     *   last. An empty list means the player is not struggling.
     */
    public fun decide(
        recentErrors: List<ErrorClass>,
        current: AssistanceProfile,
    ): Recovery {
        if (recentErrors.size < scaffoldAfter) return Recovery.CarryOn

        // "The same mistake" means the same diagnosis every time, not merely several mistakes.
        // A player making a different error each attempt is exploring, not stuck on one thing.
        val repeated = recentErrors.takeLast(isolateAfter)
        val stuckOn = repeated.distinct().singleOrNull()

        if (stuckOn != null && repeated.size >= isolateAfter) {
            return Recovery.Isolate(stuckOn, explainIsolation(stuckOn))
        }

        val worst = recentErrors.last()
        val hint = ladder.forError(current, worst) ?: return Recovery.CarryOn
        return Recovery.Scaffold(hint, explainScaffold(worst, hint))
    }

    /**
     * A sentence about this chord, not a rule about music.
     *
     * 21_CONTENT_AUTHORING_GUIDE.md §7 warns against "declaring context-dependent jazz practices
     * as universal laws", so every one of these describes what the exercise wants rather than
     * what is always true.
     */
    private fun explainScaffold(error: ErrorClass, hint: Hint): String = when (error) {
        ErrorClass.WRONG_ROOT -> "The chord is built on a different root. Showing ${hint.label.lowercase()}."
        ErrorClass.WRONG_QUALITY -> "The third is what makes this chord what it is. Showing ${hint.label.lowercase()}."
        ErrorClass.MISSING_CHORD_TONE -> "A tone this exercise requires is missing. Showing ${hint.label.lowercase()}."
        ErrorClass.WRONG_ALTERATION -> "This chord asks for an altered tension. Showing ${hint.label.lowercase()}."
        ErrorClass.WRONG_BASS -> "This exercise is about which note is underneath. Showing ${hint.label.lowercase()}."
        ErrorClass.WRONG_TOP_NOTE -> "The melody note is part of the answer here. Showing ${hint.label.lowercase()}."
        ErrorClass.EXTRA_NOTE -> "There is a note in there this voicing leaves out. Showing ${hint.label.lowercase()}."
        ErrorClass.REGISTER -> "This one wants a particular register. Showing ${hint.label.lowercase()}."
        ErrorClass.ONSET_TIMING -> "Try to strike the notes together. Showing ${hint.label.lowercase()}."
        ErrorClass.RHYTHM -> "This one is played to the beat. Showing ${hint.label.lowercase()}."
        ErrorClass.NOTHING_PLAYED -> "Nothing arrived from the keyboard. Showing ${hint.label.lowercase()}."
    }

    private fun explainIsolation(error: ErrorClass): String = when (error) {
        ErrorClass.MISSING_CHORD_TONE ->
            "Let us practise the tones of this chord on their own for a moment, then come back."

        ErrorClass.WRONG_QUALITY ->
            "Let us work on major against minor on its own for a moment, then come back."

        ErrorClass.WRONG_BASS ->
            "Let us practise inversions on their own for a moment, then come back."

        ErrorClass.ONSET_TIMING, ErrorClass.RHYTHM ->
            "Let us work on playing them together, then come back."

        else -> "Let us practise this one piece on its own for a moment, then come back."
    }

    public companion object {
        public const val DEFAULT_SCAFFOLD_AFTER: Int = 2
        public const val DEFAULT_ISOLATE_AFTER: Int = 3
    }
}
