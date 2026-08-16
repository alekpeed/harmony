package com.harmonygates.core.midi.capture

import com.harmonygates.core.midi.Controller
import com.harmonygates.core.midi.MidiEvent
import com.harmonygates.core.music.performance.CaptureCompletion
import com.harmonygates.core.music.performance.CapturePolicy
import com.harmonygates.core.music.performance.CaptureState
import com.harmonygates.core.music.performance.SustainCapturePolicy
import com.harmonygates.core.music.performance.NormalizedNoteEvent
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.pitch.MidiNote

/**
 * Decides when a chord has been played.
 *
 * The whole point is stated in 06_PERFORMANCE_EVALUATION_AND_SCORING.md §7: "Never auto-submit
 * on the first note." Humans do not depress a chord on one millisecond, and an evaluator fed
 * the first note of a Cmaj7 would confidently mark it a C.
 *
 * So notes accumulate, and the attempt completes when one of these happens:
 *
 * - the keyboard goes quiet for the policy's window, plus a stabilisation pause
 * - the player lifts every key
 * - the roll window runs out while notes are still arriving
 * - something outside asks — a beat boundary, or a button
 *
 * Time arrives through [onTick] rather than a timer inside the class. That keeps the state
 * machine pure and synchronous, so a 250 ms rolled chord is a test that runs instantly, and it
 * is the host's business whether ticks come from a coroutine or from a test.
 */
public class OnsetAggregator(
    private var policy: CapturePolicy = CapturePolicy.GuidedChord,
) {

    public var state: CaptureState = CaptureState.IDLE
        private set

    private var armedAtNanos = 0L
    private var firstOnsetNanos: Long? = null
    private var lastOnsetNanos: Long? = null
    private var sustainDown = false
    private var sustainUsed = false

    /** Onsets in order, each with a release time once the key comes up. */
    private val onsets = mutableListOf<MutableNote>()
    private val heldNotes = LinkedHashSet<MidiNote>()

    private data class MutableNote(
        val note: MidiNote,
        val velocity: Int,
        val onsetNanos: Long,
        var releaseNanos: Long? = null,
        var heldByPedal: Boolean = false,
    )

    /** Arms capture. Anything from a previous attempt is dropped. */
    public fun arm(nowNanos: Long, policy: CapturePolicy = this.policy) {
        this.policy = policy
        armedAtNanos = nowNanos
        firstOnsetNanos = null
        lastOnsetNanos = null
        sustainUsed = false
        onsets.clear()
        heldNotes.clear()
        state = CaptureState.ARMED
    }

    /** Abandons capture without producing an attempt. */
    public fun cancel() {
        state = CaptureState.IDLE
        onsets.clear()
        heldNotes.clear()
    }

    /**
     * Feeds one event in.
     *
     * Returns a completed attempt when this event finished the chord — the player lifting the
     * last key — and null otherwise.
     */
    public fun onEvent(event: MidiEvent): PerformanceAttempt? {
        if (state != CaptureState.ARMED && state != CaptureState.COLLECTING) return null

        when (event) {
            is MidiEvent.NoteOn -> {
                if (!accepts(event.note)) return null
                state = CaptureState.COLLECTING
                // A re-struck key is one note, not two: replace the earlier onset rather than
                // recording a phantom second voice.
                onsets.removeAll { it.note == event.note && it.releaseNanos == null }
                onsets += MutableNote(event.note, event.velocity, event.timestampNanos)
                heldNotes += event.note
                if (firstOnsetNanos == null) firstOnsetNanos = event.timestampNanos
                lastOnsetNanos = event.timestampNanos
            }

            is MidiEvent.NoteOff -> {
                if (!accepts(event.note)) return null
                val wasHeld = heldNotes.remove(event.note)
                onsets.lastOrNull { it.note == event.note && it.releaseNanos == null }?.let { entry ->
                    entry.releaseNanos = event.timestampNanos
                    if (sustainDown && wasHeld) entry.heldByPedal = true
                }
                // Lifting your hands with the pedal down is not finishing a chord — the notes
                // are still sounding and the player may well still be playing. Waiting for the
                // quiet window instead is what lets a note caught by the pedal be recognised as
                // a remnant rather than ending the attempt on its own.
                if (policy.completeOnAllKeysReleased &&
                    !sustainDown &&
                    state == CaptureState.COLLECTING &&
                    heldNotes.isEmpty()
                ) {
                    // Only finish if there is actually an answer to judge. A player who brushes
                    // one key and lets go has not played a chord, and completing there would
                    // hand the evaluator an empty attempt while they were still reaching.
                    val prospective = buildAttempt(event.timestampNanos, CaptureCompletion.AllKeysReleased)
                    if (prospective.finalEffectiveNotes.isNotEmpty()) {
                        state = CaptureState.COMPLETED
                        return prospective
                    }
                }
            }

            is MidiEvent.ControlChange -> {
                if (event.isSustainPedal) {
                    sustainDown = event.isSwitchedOn
                    if (sustainDown) sustainUsed = true
                } else if (event.isAllNotesOff || event.controller == Controller.ALL_SOUND_OFF) {
                    heldNotes.clear()
                }
            }

            else -> Unit
        }
        return null
    }

    /**
     * Advances time.
     *
     * Returns a completed attempt when the quiet window or the roll window has elapsed.
     */
    public fun onTick(nowNanos: Long): PerformanceAttempt? {
        if (state != CaptureState.COLLECTING) return null
        val lastOnset = lastOnsetNanos ?: return null
        val firstOnset = firstOnsetNanos ?: return null

        if (nowNanos - firstOnset >= policy.maxRollWindowNanos) {
            return complete(nowNanos, CaptureCompletion.MaxRollWindowReached)
        }
        if (nowNanos - lastOnset >= policy.quietWindowNanos + policy.stabilisationNanos) {
            return complete(nowNanos, CaptureCompletion.OnsetsSettled)
        }
        return null
    }

    /** Ends capture now, whatever state it is in. Used by a beat boundary or an explicit button. */
    public fun submit(nowNanos: Long): PerformanceAttempt =
        complete(nowNanos, CaptureCompletion.ExplicitSubmit)

    /**
     * Ends capture because the keyboard went away.
     *
     * The attempt is still produced, marked [CaptureCompletion.DeviceLost], so the evaluator can
     * return `ABORTED_DEVICE_LOSS` rather than a wrong answer. 05_MIDI_INPUT_ENGINE.md §9:
     * losing a cable must not cost the player anything.
     */
    public fun deviceLost(nowNanos: Long): PerformanceAttempt =
        complete(nowNanos, CaptureCompletion.DeviceLost)

    private fun accepts(note: MidiNote): Boolean =
        policy.acceptedRange?.contains(note.value) ?: true

    private fun complete(nowNanos: Long, completion: CaptureCompletion): PerformanceAttempt {
        state = CaptureState.COMPLETED
        return buildAttempt(nowNanos, completion)
    }

    /** Builds the attempt without changing state, so a completion can be considered first. */
    private fun buildAttempt(nowNanos: Long, completion: CaptureCompletion): PerformanceAttempt {
        val events = onsets.map {
            NormalizedNoteEvent(
                note = it.note,
                velocity = it.velocity,
                onsetNanos = it.onsetNanos,
                releaseNanos = it.releaseNanos,
                heldByPedal = it.heldByPedal,
            )
        }

        val effective = effectiveNotes(events)
        val onsetTimes = events.map { it.onsetNanos }
        val spread = if (onsetTimes.size >= 2) onsetTimes.max() - onsetTimes.min() else null

        return PerformanceAttempt(
            startedAtNanos = armedAtNanos,
            completedAtNanos = nowNanos,
            noteEvents = events,
            finalEffectiveNotes = effective.sorted(),
            onsetSpreadNanos = spread,
            sustainUsed = sustainUsed,
            completion = completion,
        )
    }

    /**
     * Reduces the event history to the notes actually being judged.
     *
     * Both filters turn on the same question: was the key let go *while the chord was still
     * arriving*, or as part of finishing it? Letting go is how a player ends a chord, so a
     * release after the final onset is never evidence that the note was unintended — an earlier
     * version of this filter discarded every note of a short chord and marked correct answers
     * empty.
     *
     * Released early, and briefly: a slip, like catching the black key next door.
     * Released early, under the pedal: a remnant that is ringing but not being played.
     *
     * The full history stays in `noteEvents` either way, because the diagnosis "you caught the
     * neighbouring key" needs the note that was dropped.
     */
    private fun effectiveNotes(events: List<NormalizedNoteEvent>): List<MidiNote> {
        if (events.isEmpty()) return emptyList()
        val lastOnset = events.maxOf { it.onsetNanos }

        fun releasedWhileStillPlaying(event: NormalizedNoteEvent): Boolean =
            event.releaseNanos?.let { it < lastOnset } == true

        return events.filterNot { event ->
            val slip = policy.graceNanos > 0 &&
                releasedWhileStillPlaying(event) &&
                event.releasedWithin(policy.graceNanos)
            val pedalRemnant = policy.sustain != SustainCapturePolicy.IncludeSustained &&
                event.heldByPedal &&
                releasedWhileStillPlaying(event)
            slip || pedalRemnant
        }.map { it.note }.distinct()
    }
}
