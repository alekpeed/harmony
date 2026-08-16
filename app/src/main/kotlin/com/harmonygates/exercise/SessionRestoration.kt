package com.harmonygates.exercise

/**
 * What a session needs in order to survive process death.
 *
 * 14_TESTING_AND_QUALITY.md §9 makes "process death/state restoration considered" part of the
 * definition of done. Considering it here has an unusually cheap answer, because the exercise
 * generator is deterministic: given the same seed and the same position in the session, it
 * produces the same exercises in the same order. So none of the exercises have to be stored —
 * only the two numbers that regenerate them.
 *
 * What is *not* restored is the current attempt. A player whose tablet was killed mid-chord is
 * given that exercise again rather than being told they got it wrong, which is the same rule
 * 02_GAME_LOOP_AND_PROGRESSION.md applies to an interrupted session.
 *
 * The attempts already answered are not in here either: they were written to the database as
 * each one was judged, so they are not at risk in the first place.
 */
data class SessionRestoration(
    /** The row every attempt of this session is a child of. */
    val sessionId: String,
    /** Regenerates the exercises. */
    val seed: Long,
    /** Null for free practice. */
    val gateId: String?,
    /** How far through the session the player had got. */
    val completedExercises: Int,
) {
    init {
        require(sessionId.isNotBlank()) { "A session id is what the attempts hang off" }
        require(completedExercises >= 0) { "A session cannot be less than nothing through" }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        KEY_SESSION_ID to sessionId,
        KEY_SEED to seed,
        KEY_GATE_ID to gateId,
        KEY_COMPLETED to completedExercises,
    )

    companion object {
        const val KEY_SESSION_ID: String = "session.id"
        const val KEY_SEED: String = "session.seed"
        const val KEY_GATE_ID: String = "session.gateId"
        const val KEY_COMPLETED: String = "session.completed"

        /**
         * Reads a saved session back, or null when there is nothing usable to read.
         *
         * Null rather than an exception, and null rather than a half-populated object: a saved
         * state that has lost its seed cannot reproduce the session it claims to be, and
         * starting a fresh session is a better outcome than resuming a different one under the
         * old session's id.
         */
        fun fromMap(saved: Map<String, Any?>): SessionRestoration? {
            val sessionId = saved[KEY_SESSION_ID] as? String ?: return null
            if (sessionId.isBlank()) return null
            val seed = saved[KEY_SEED] as? Long ?: return null
            val completed = saved[KEY_COMPLETED] as? Int ?: return null
            if (completed < 0) return null
            return SessionRestoration(
                sessionId = sessionId,
                seed = seed,
                gateId = saved[KEY_GATE_ID] as? String,
                completedExercises = completed,
            )
        }
    }
}
