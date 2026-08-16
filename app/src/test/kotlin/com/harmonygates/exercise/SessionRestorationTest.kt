package com.harmonygates.exercise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Surviving a process death.
 *
 * 14_TESTING_AND_QUALITY.md §9 puts state restoration in the definition of done, and this is the
 * decision-shaped part of it: what is worth saving, and what a half-written saved state should
 * do. The `SavedStateHandle` plumbing around it is Android's and is not what goes wrong.
 */
class SessionRestorationTest {

    private val session = SessionRestoration(
        sessionId = "session-1",
        seed = 12345L,
        gateId = "gate.sevenths.build",
        completedExercises = 7,
    )

    @Test
    fun `a saved session reads back as itself`() {
        assertEquals(session, SessionRestoration.fromMap(session.toMap()))
    }

    @Test
    fun `free practice saves without a gate`() {
        val practice = session.copy(gateId = null)

        assertEquals(practice, SessionRestoration.fromMap(practice.toMap()))
    }

    @Test
    fun `only four things are saved`() {
        // The exercises themselves are deliberately absent: the generator is deterministic, so
        // the seed and the position regenerate them. Anything else stored here could disagree
        // with the database.
        assertEquals(
            setOf(
                SessionRestoration.KEY_SESSION_ID,
                SessionRestoration.KEY_SEED,
                SessionRestoration.KEY_GATE_ID,
                SessionRestoration.KEY_COMPLETED,
            ),
            session.toMap().keys,
        )
    }

    @Test
    fun `nothing saved means a fresh session, not a broken one`() {
        assertNull(SessionRestoration.fromMap(emptyMap()))
    }

    @Test
    fun `a saved state missing its seed is refused`() {
        // Without the seed the exercises cannot be reproduced, so resuming under the old session
        // id would attach new attempts to a session that was about something else.
        val incomplete = session.toMap() - SessionRestoration.KEY_SEED

        assertNull(SessionRestoration.fromMap(incomplete))
    }

    @Test
    fun `a saved state missing its session id is refused`() {
        assertNull(SessionRestoration.fromMap(session.toMap() - SessionRestoration.KEY_SESSION_ID))
        assertNull(SessionRestoration.fromMap(session.toMap() + (SessionRestoration.KEY_SESSION_ID to "")))
    }

    @Test
    fun `a saved state with the wrong types is refused rather than coerced`() {
        val wrongTypes = session.toMap() + (SessionRestoration.KEY_SEED to "12345")

        assertNull(
            SessionRestoration.fromMap(wrongTypes),
            "A seed that arrives as text is a bug somewhere, not something to parse hopefully",
        )
    }

    @Test
    fun `a negative position is refused`() {
        assertNull(SessionRestoration.fromMap(session.toMap() + (SessionRestoration.KEY_COMPLETED to -1)))
        assertFailsWith<IllegalArgumentException> { session.copy(completedExercises = -1) }
    }

    @Test
    fun `a blank session id is refused at construction`() {
        assertFailsWith<IllegalArgumentException> { session.copy(sessionId = " ") }
    }

    @Test
    fun `an unknown extra key does not stop a restore`() {
        val withExtra = session.toMap() + ("something.added.later" to 1)

        assertEquals(session, SessionRestoration.fromMap(withExtra))
    }
}
