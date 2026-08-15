package com.harmonygates.core.music.session

import com.harmonygates.core.music.exercise.ExerciseInstance
import com.harmonygates.core.music.exercise.ExercisePolicy
import com.harmonygates.core.music.exercise.PresentationSpec
import com.harmonygates.core.music.performance.EvaluationResult
import com.harmonygates.core.music.performance.PerformanceAttempt
import com.harmonygates.core.music.performance.Verdict
import com.harmonygates.core.music.voicing.Inversion

/** How a session was set up. */
public data class SessionConfig(
    val policy: ExercisePolicy,
    /** Reproduces the whole session, exercise by exercise. */
    val seed: Long,
    val exerciseCount: Int = policy.sessionLength,
) {
    init {
        require(exerciseCount > 0) { "A session needs at least one exercise" }
    }
}

/**
 * What the screen draws.
 *
 * 10_ANDROID_ARCHITECTURE.md §5: the domain-rich [ExerciseInstance] is mapped to this, and the
 * mapper decides which assistance channels are visible "without altering the underlying answer".
 * Everything here is already a string or a number, so no screen has to ask the domain anything.
 */
public data class ExercisePresentationModel(
    val exerciseNumber: Int,
    val totalExercises: Int,
    val chordSymbol: String?,
    val spelledNoteNames: List<String>,
    val keyboardTargets: List<Int>,
    val inversionLabel: String?,
    val instruction: String,
) {
    public companion object {
        /**
         * Maps an instance through its presentation spec.
         *
         * A channel the exercise did not enable is absent here, not merely hidden — so a screen
         * cannot accidentally reveal the answer by rendering a field it was handed.
         */
        public fun of(
            instance: ExerciseInstance,
            index: Int,
            total: Int,
        ): ExercisePresentationModel {
            val spec: PresentationSpec = instance.presentation
            return ExercisePresentationModel(
                exerciseNumber = index + 1,
                totalExercises = total,
                chordSymbol = instance.chord.symbol.takeIf { spec.showChordSymbol },
                spelledNoteNames = if (spec.showSpelledNoteNames) {
                    instance.spelledTones.map { it.toString() }
                } else {
                    emptyList()
                },
                keyboardTargets = if (spec.showKeyboardTargets) instance.targetNotes else emptyList(),
                inversionLabel = instance.inversion
                    .takeIf { spec.showInversionLabel && it != Inversion.OTHER }
                    ?.let { inversionLabel(it) },
                instruction = instructionFor(instance),
            )
        }

        private fun inversionLabel(inversion: Inversion): String = when (inversion) {
            Inversion.ROOT -> "Root position"
            Inversion.FIRST -> "First inversion"
            Inversion.SECOND -> "Second inversion"
            Inversion.THIRD -> "Third inversion"
            Inversion.OTHER -> "Any inversion"
        }

        private fun instructionFor(instance: ExerciseInstance): String =
            when (instance.inversion) {
                Inversion.ROOT -> "Play this chord in root position"
                Inversion.OTHER -> "Play this chord"
                else -> "Play this chord, ${inversionLabel(instance.inversion).lowercase()}"
            }
    }
}

/** Notes currently sounding, for live feedback while the player is still holding a chord. */
public data class LivePerformanceState(
    val exercise: ExercisePresentationModel,
    val soundingNotes: List<Int> = emptyList(),
)

/** Why a session paused. */
public enum class PauseReason {
    DeviceDisconnected,
    AppBackgrounded,
    PlayerRequested,
}

/** Why an exercise was skipped. */
public enum class SkipReason {
    PlayerRequested,
    DeviceUnavailable,
    Timeout,
}

/** How a session went. */
public data class SessionSummary(
    val total: Int,
    val attempted: Int,
    val correct: Int,
    val partial: Int,
    val incorrect: Int,
    val skipped: Int,
    val hintsUsed: Int,
    val medianResponseMillis: Long?,
) {
    /** 0..1 over exercises that produced evidence. Inconclusive attempts are excluded. */
    public val accuracy: Double get() = if (attempted == 0) 0.0 else correct.toDouble() / attempted

    public companion object {
        internal fun of(records: List<AttemptRecord>, total: Int, hintsUsed: Int): SessionSummary {
            val conclusive = records.filterNot { it.result.verdict.isInconclusive }
            val latencies = records.mapNotNull { it.result.timing?.responseLatencyMillis }.sorted()
            return SessionSummary(
                total = total,
                attempted = conclusive.size,
                correct = conclusive.count { it.result.isCorrect },
                partial = conclusive.count { it.result.verdict == Verdict.PARTIAL },
                incorrect = conclusive.count { it.result.verdict == Verdict.INCORRECT },
                skipped = records.count { it.skipped },
                hintsUsed = hintsUsed,
                medianResponseMillis = latencies.getOrNull(latencies.size / 2),
            )
        }
    }
}

/** One exercise's outcome, kept so the summary and later mastery updates have the detail. */
public data class AttemptRecord(
    val instance: ExerciseInstance,
    val attempt: PerformanceAttempt,
    val result: EvaluationResult,
    val skipped: Boolean = false,
    val hintsUsed: Int = 0,
)

/**
 * Where a session is (20_BOOTSTRAP_AND_MODULE_CONTRACTS.md §2).
 *
 * A closed set rather than a bag of flags, so a screen cannot render "showing feedback" and
 * "waiting for a chord" at the same time, and every state carries exactly what it needs.
 */
public sealed interface ExerciseSessionState {
    public data object Idle : ExerciseSessionState

    public data class Loading(val policyId: String?) : ExerciseSessionState

    /** The target is on screen; capture is not armed yet. */
    public data class Presenting(val exercise: ExercisePresentationModel) : ExerciseSessionState

    /** Capture is armed and listening. */
    public data class Armed(val exercise: ExercisePresentationModel) : ExerciseSessionState

    /** Notes are arriving. */
    public data class Capturing(val live: LivePerformanceState) : ExerciseSessionState

    public data class Feedback(
        val exercise: ExercisePresentationModel,
        val result: EvaluationResult,
        val nextAvailable: Boolean,
    ) : ExerciseSessionState

    public data class Paused(val reason: PauseReason) : ExerciseSessionState

    public data class Completed(val summary: SessionSummary) : ExerciseSessionState

    /** The exercise on screen, when there is one. */
    public val visibleExercise: ExercisePresentationModel?
        get() = when (this) {
            is Presenting -> exercise
            is Armed -> exercise
            is Capturing -> live.exercise
            is Feedback -> exercise
            else -> null
        }
}
