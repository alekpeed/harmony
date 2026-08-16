package com.harmonygates.core.music.performance


/**
 * What to do about notes still ringing from the previous attempt (05_MIDI_INPUT_ENGINE.md §5).
 *
 * The default matters: "Default guided chord mode should avoid marking a correct new chord
 * wrong because of stale sustain." A player who leaves the pedal down between exercises is not
 * making a mistake about the chord in front of them.
 */
public enum class SustainCapturePolicy {
    /** Judge only what the fingers are on. The default for guided chord work. */
    IgnoreRemnants,

    /** Judge everything a listener would hear, pedal included. */
    IncludeSustained,

    /** Refuse to arm until the pedal comes up. For exercises about pedalling itself. */
    RequirePedalRelease,
}

/**
 * Tuning for the onset aggregation state machine (05_MIDI_INPUT_ENGINE.md §6).
 *
 * The spec is explicit that these are "starting default values for user testing, not permanent
 * constants", so they live in a policy an exercise supplies rather than as constants in the
 * state machine.
 *
 * @param quietWindowMillis how long the keyboard must go quiet before a chord counts as finished.
 * @param maxRollWindowMillis longest a chord may take to arrive before it is taken as-is.
 * @param stabilisationMillis extra pause after the quiet window, for a last correction.
 * @param accidentalNoteGraceMillis a key struck and released faster than this, before the
 *   attempt completes, is treated as a slip rather than part of the answer. Zero disables it,
 *   which is what a timed or challenge exercise wants.
 * @param completeOnAllKeysReleased finish as soon as the player lifts every finger.
 */
public data class CapturePolicy(
    val onsetPolicy: OnsetPolicy = OnsetPolicy.NormalRoll,
    val quietWindowMillis: Int = DEFAULT_QUIET_WINDOW_MS,
    val maxRollWindowMillis: Int = DEFAULT_MAX_ROLL_MS,
    val stabilisationMillis: Int = DEFAULT_STABILISATION_MS,
    val accidentalNoteGraceMillis: Int = DEFAULT_GRACE_MS,
    val sustain: SustainCapturePolicy = SustainCapturePolicy.IgnoreRemnants,
    val completeOnAllKeysReleased: Boolean = true,
    /** Notes outside this range are ignored entirely, e.g. a stuck key on a broken controller. */
    val acceptedRange: IntRange? = null,
) {
    init {
        require(quietWindowMillis > 0) { "The quiet window must be positive" }
        require(maxRollWindowMillis >= quietWindowMillis) {
            "A roll window shorter than the quiet window would end every chord immediately"
        }
        require(stabilisationMillis >= 0) { "Stabilisation cannot be negative" }
        require(accidentalNoteGraceMillis >= 0) { "The grace period cannot be negative" }
    }

    public val quietWindowNanos: Long get() = quietWindowMillis * PerformanceAttempt.NANOS_PER_MILLI
    public val maxRollWindowNanos: Long get() = maxRollWindowMillis * PerformanceAttempt.NANOS_PER_MILLI
    public val stabilisationNanos: Long get() = stabilisationMillis * PerformanceAttempt.NANOS_PER_MILLI
    public val graceNanos: Long get() = accidentalNoteGraceMillis * PerformanceAttempt.NANOS_PER_MILLI

    public companion object {
        // The starting values from §6. Instrument-test these on real hardware before treating
        // any of them as settled.
        private const val DEFAULT_QUIET_WINDOW_MS = 80
        private const val DEFAULT_MAX_ROLL_MS = 300
        private const val DEFAULT_STABILISATION_MS = 40
        private const val DEFAULT_GRACE_MS = 70

        /** Guided chord work: a rolled voicing is fine and a slipped key is forgiven. */
        public val GuidedChord: CapturePolicy = CapturePolicy()

        /**
         * Challenge mode: the chord must land together and a wrong key counts immediately.
         *
         * §7 allows exactly this — "in challenge/timed mode, accidental notes can count
         * immediately if the policy says so".
         */
        public val Challenge: CapturePolicy = CapturePolicy(
            onsetPolicy = OnsetPolicy.Together,
            accidentalNoteGraceMillis = 0,
            stabilisationMillis = 0,
        )
    }
}
