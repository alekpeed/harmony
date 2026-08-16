package com.harmonygates.exercise

import com.harmonygates.core.music.campaign.GateId
import com.harmonygates.core.music.exercise.ExercisePolicyId

/**
 * What a chord-gate session was opened for.
 *
 * A gate session and free practice run the same engine over the same policies; the only
 * differences are where the policy comes from and whether the result counts towards a gate.
 * Keeping that to one sealed type, rather than two screens, is what stops
 * 02_GAME_LOOP_AND_PROGRESSION.md §3's session phases from having to be written twice.
 */
sealed interface SessionRequest {

    /** A gate from the campaign. Its policy comes from the curriculum. */
    data class Gate(override val gateId: GateId) : SessionRequest

    /** Free practice against a named policy. Nothing is gated on the outcome. */
    data class Practice(val policyId: ExercisePolicyId) : SessionRequest {
        override val gateId: GateId? get() = null
    }

    /** The gate this session counts towards, or null for free practice. */
    val gateId: GateId?

    companion object {
        /** What the home screen's Quick Practice control opens. */
        val QuickPractice: SessionRequest =
            Practice(ExercisePolicyId("policy.practice.mixed_sevenths"))
    }
}
