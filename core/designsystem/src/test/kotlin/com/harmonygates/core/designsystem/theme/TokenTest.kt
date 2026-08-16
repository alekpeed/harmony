package com.harmonygates.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The token set, checked as a system rather than as a list of hexes.
 *
 * 12_UI_UX_AND_FIGMA_HANDOFF.md §10 asks for "sufficient contrast" and 18_ACCEPTANCE_CRITERIA.md
 * adds that correctness must not be carried by colour alone. Both are properties of the palette
 * as a whole, which means they can be checked here rather than discovered on a tablet in a
 * sunlit room. The Figma pass will change these values; these tests are what tell it whether the
 * change is still legible.
 */
class TokenTest {

    private val colors = ApprovedColors

    // --- Contrast ----------------------------------------------------------------------------

    @Test
    fun `body text is readable on every surface it is drawn on`() {
        val surfaces = mapOf(
            "backgroundBase" to colors.backgroundBase,
            "backgroundSunken" to colors.backgroundSunken,
            "surface" to colors.surface,
            "surfaceRaised" to colors.surfaceRaised,
            "surfaceOverlay" to colors.surfaceOverlay,
        )

        for ((name, surface) in surfaces) {
            val ratio = contrast(colors.textPrimary, surface)
            assertTrue(
                ratio >= AA_BODY,
                "Primary text on $name is ${format(ratio)}:1, below the $AA_BODY:1 needed to read it",
            )
        }
    }

    @Test
    fun `secondary text clears the bar too, and tertiary clears the large-text bar`() {
        assertTrue(
            contrast(colors.textSecondary, colors.surface) >= AA_BODY,
            "Secondary text is used for whole sentences, so it needs full contrast",
        )
        assertTrue(
            contrast(colors.textTertiary, colors.surface) >= AA_LARGE,
            "Tertiary text is used for labels and disabled states, at large sizes only",
        )
    }

    @Test
    fun `text on an accent fill is readable`() {
        // A brass button with text on it is the commonest control in the app.
        assertTrue(
            contrast(colors.onAccent, colors.accentPrimary) >= AA_BODY,
            "Text on the primary accent is ${format(contrast(colors.onAccent, colors.accentPrimary))}:1",
        )
        assertTrue(contrast(colors.onAccent, colors.accentSecondary) >= AA_BODY)
    }

    @Test
    fun `every feedback colour is visible against the surface it appears on`() {
        val feedback = mapOf(
            "success" to colors.feedbackSuccess,
            "warning" to colors.feedbackWarning,
            "error" to colors.feedbackError,
            "neutral" to colors.feedbackNeutral,
        )

        for ((name, colour) in feedback) {
            assertTrue(
                contrast(colour, colors.surface) >= AA_LARGE,
                "The $name colour is ${format(contrast(colour, colors.surface))}:1 against the surface",
            )
        }
    }

    @Test
    fun `notation is readable against the ground it is drawn on`() {
        assertTrue(contrast(colors.noteHead, colors.backgroundBase) >= AA_BODY)
        assertTrue(contrast(colors.staffLine, colors.backgroundBase) >= AA_LARGE)
        assertTrue(contrast(colors.accidental, colors.backgroundBase) >= AA_BODY)
    }

    @Test
    fun `a key label is readable on the key it sits on`() {
        assertTrue(contrast(colors.keyLabel, colors.keyWhite) >= AA_BODY)
        assertTrue(contrast(colors.textPrimary, colors.keyBlack) >= AA_BODY)
    }

    // --- Distinctness --------------------------------------------------------------------------

    @Test
    fun `no two feedback colours are the same colour`() {
        val feedback = listOf(
            colors.feedbackSuccess,
            colors.feedbackWarning,
            colors.feedbackError,
            colors.feedbackNeutral,
        )

        assertEquals(feedback.size, feedback.toSet().size, "Two verdicts would look identical")
    }

    @Test
    fun `no two gate states are the same colour`() {
        val gates = listOf(
            colors.gateLocked,
            colors.gateAvailable,
            colors.gateInProgress,
            colors.gateMastered,
        )

        assertEquals(gates.size, gates.toSet().size, "Two gate states would look identical")
    }

    @Test
    fun `the surface levels get lighter in order`() {
        // A raised panel that was darker than the thing under it would read as a hole.
        val levels = listOf(
            colors.backgroundSunken,
            colors.backgroundBase,
            colors.surface,
            colors.surfaceRaised,
            colors.surfaceOverlay,
        ).map { luminance(it) }

        assertEquals(levels.sorted(), levels, "Surface levels are out of order: $levels")
    }

    // --- Scales --------------------------------------------------------------------------------

    @Test
    fun `the spacing scale ascends`() {
        val scale = with(DefaultSpacing) { listOf(hairline, tight, small, medium, large, section) }

        assertEquals(scale.sortedBy { it.value }, scale, "The spacing scale is not a scale")
    }

    @Test
    fun `the type scale descends from the chord symbol`() {
        val scale = with(DefaultTypography) {
            listOf(chordSymbol, display, heading, title, body, label, caption)
        }

        assertEquals(
            scale.sortedByDescending { it.value },
            scale,
            "The type scale is not ordered, so a heading could be smaller than its body",
        )
        assertTrue(
            DefaultTypography.chordSymbol.value >= MINIMUM_CHORD_SYMBOL_SP,
            "The chord symbol is read from a keyboard bench and must stay large",
        )
    }

    @Test
    fun `the touch target is bigger than the platform minimum`() {
        assertTrue(
            DefaultSpacing.minimumTouchTarget.value >= PLATFORM_MINIMUM_DP,
            "A player is further from a propped-up tablet than from a phone in their hand",
        )
    }

    @Test
    fun `reduced motion removes the movement and nothing else`() {
        val reduced = DefaultMotion.reduced()

        assertEquals(0, reduced.quickMillis)
        assertEquals(0, reduced.standardMillis)
        assertEquals(0, reduced.deliberateMillis)
        assertEquals(0, reduced.celebrationMillis)
        assertEquals(
            DefaultMotion.standardEasing,
            reduced.standardEasing,
            "Easing curves are meaningless at zero duration but should not be replaced",
        )
    }

    @Test
    fun `feedback is fast and celebration is not`() {
        // 12 §9: feedback "fast and restrained"; a richer moment is allowed only after scoring.
        assertTrue(DefaultMotion.quickMillis <= FAST_FEEDBACK_MILLIS)
        assertTrue(DefaultMotion.standardMillis <= FAST_FEEDBACK_MILLIS * 2)
        assertTrue(DefaultMotion.celebrationMillis > DefaultMotion.deliberateMillis)
    }

    // --- Contrast arithmetic ---------------------------------------------------------------------

    /** WCAG relative luminance. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= LOW_CHANNEL) c / LOW_DIVISOR else ((c + OFFSET) / SCALE).pow(GAMMA)
        }
        return RED_WEIGHT * channel(color.red) +
            GREEN_WEIGHT * channel(color.green) +
            BLUE_WEIGHT * channel(color.blue)
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + AMBIENT) / (min(a, b) + AMBIENT)
    }

    private fun format(ratio: Double): String = ((ratio * 100).toInt() / 100.0).toString()

    private companion object {
        const val AA_BODY = 4.5
        const val AA_LARGE = 3.0
        const val AMBIENT = 0.05
        const val LOW_CHANNEL = 0.03928
        const val LOW_DIVISOR = 12.92
        const val OFFSET = 0.055
        const val SCALE = 1.055
        const val GAMMA = 2.4
        const val RED_WEIGHT = 0.2126
        const val GREEN_WEIGHT = 0.7152
        const val BLUE_WEIGHT = 0.0722
        const val MINIMUM_CHORD_SYMBOL_SP = 48f
        const val PLATFORM_MINIMUM_DP = 48f
        const val FAST_FEEDBACK_MILLIS = 150
    }
}
