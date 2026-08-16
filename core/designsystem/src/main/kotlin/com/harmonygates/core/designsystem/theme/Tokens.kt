package com.harmonygates.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens.
 *
 * 12_UI_UX_AND_FIGMA_HANDOFF.md §4: "Do not scatter literal values across Compose." Every group
 * that section names has a token here, and every screen addresses colour, spacing, shape, motion
 * and type through [HarmonyTheme] rather than through a literal.
 *
 * **Where the values come from.** The colours are sampled from the approved artwork in
 * `interface/` — `harmony_home_approved.jpg` and `assets/progression-run-background.png` — not
 * chosen here. Those plates are a dark, warm world: near-black grounds around `#0A0908`, brass
 * and amber accents around `#B07020` rising to `#F0C090`, and warm off-white text near
 * `#F8F0E8`. Reading the palette out of the approved screens is the only way for a Compose
 * surface drawn *over* a painted plate to sit on it rather than beside it.
 *
 * Two groups are not in the artwork and are marked where they appear: the feedback colours and
 * the piano key states, neither of which the two approved screens contain. They are provisional
 * and are the first things Phase 12's Figma pass should replace. Nothing depends on their exact
 * value: 18_ACCEPTANCE_CRITERIA.md forbids carrying correctness by colour alone, so every state
 * that uses them also carries a glyph or an outline.
 */
@Immutable
public data class HarmonyColorTokens(
    // --- Background and surface levels ---------------------------------------------------
    /** The floor of the app. What a painted plate is composited onto. */
    val backgroundBase: Color,
    /** Deeper than the floor: wells, insets, the space behind a scrolling list. */
    val backgroundSunken: Color,
    /** The lowest level content sits on. */
    val surface: Color,
    /** A panel lifted off the surface. */
    val surfaceRaised: Color,
    /** A sheet or dialog over everything else. */
    val surfaceOverlay: Color,
    /** A scrim under an overlay. Semi-transparent by design. */
    val scrim: Color,

    // --- Accents -------------------------------------------------------------------------
    /** Brass. The colour of an actionable thing. */
    val accentPrimary: Color,
    /** Lit amber. Emphasis on top of brass, and the current position in the campaign. */
    val accentSecondary: Color,
    /** Text and glyphs drawn on an accent fill. */
    val onAccent: Color,

    // --- Text hierarchy ------------------------------------------------------------------
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val outline: Color,

    // --- Feedback ------------------------------------------------------------------------
    /**
     * Feedback colours. Provisional: not present in the approved artwork.
     *
     * These reinforce a verdict that is already carried by a glyph and by words, which is what
     * lets them be provisional without the app being wrong in the meantime.
     */
    val feedbackSuccess: Color,
    val feedbackWarning: Color,
    val feedbackError: Color,
    val feedbackNeutral: Color,

    // --- Notation ------------------------------------------------------------------------
    val staffLine: Color,
    val staffLedger: Color,
    val noteHead: Color,
    val accidental: Color,
    val clef: Color,

    // --- Piano ---------------------------------------------------------------------------
    /** Piano surfaces. Provisional: no keyboard appears in the two approved screens. */
    val keyWhite: Color,
    val keyWhitePressed: Color,
    val keyBlack: Color,
    val keyBlackPressed: Color,
    val keyBorder: Color,
    val keyLabel: Color,
    /** A key the exercise is asking for, before anything is played. */
    val keyTarget: Color,

    // --- Gates ---------------------------------------------------------------------------
    val gateLocked: Color,
    val gateAvailable: Color,
    val gateInProgress: Color,
    val gateMastered: Color,
) {
    /** Alias kept for the screens written before the token set was completed. */
    public val background: Color get() = backgroundBase

    /** @see textPrimary */
    public val onSurface: Color get() = textPrimary

    /** @see textSecondary */
    public val onSurfaceMuted: Color get() = textSecondary

    /** @see accentPrimary */
    public val accent: Color get() = accentPrimary

    /** @see feedbackSuccess */
    public val correct: Color get() = feedbackSuccess

    /** @see feedbackError */
    public val incorrect: Color get() = feedbackError

    /** @see feedbackWarning */
    public val partial: Color get() = feedbackWarning

    /** @see feedbackNeutral */
    public val awaitingInput: Color get() = feedbackNeutral
}

@Immutable
public data class HarmonySpacingTokens(
    val hairline: Dp,
    val tight: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val section: Dp,
    /**
     * Smallest comfortable touch target at tablet playing distance.
     *
     * The player is sitting at a keyboard, further from the screen than a phone user, so this
     * is above the platform minimum on purpose.
     */
    val minimumTouchTarget: Dp,
)

/** Corner radii and elevation. 12 §4 asks for both by name. */
@Immutable
public data class HarmonyShapeTokens(
    val radiusNone: Dp,
    val radiusSmall: Dp,
    val radiusMedium: Dp,
    val radiusLarge: Dp,
    /** Big enough to always round a chip's ends, whatever its height. */
    val radiusPill: Dp,
    val elevationFlat: Dp,
    val elevationRaised: Dp,
    val elevationFloating: Dp,
    val elevationOverlay: Dp,
)

/**
 * Durations and easing.
 *
 * 12 §9 wants feedback "fast and restrained", and §10 wants a reduced-motion mode — which is
 * why durations are tokens rather than literals: [HarmonyMotionTokens.reduced] returns the same
 * set with every duration collapsed, so honouring the preference is one substitution rather
 * than an audit of every animation in the app.
 */
@Immutable
public data class HarmonyMotionTokens(
    val instantMillis: Int,
    val quickMillis: Int,
    val standardMillis: Int,
    val deliberateMillis: Int,
    /** Gate completion, which 12 §9 allows to be richer — after scoring, never during input. */
    val celebrationMillis: Int,
    val standardEasing: Easing,
    val emphasizedEasing: Easing,
    val exitEasing: Easing,
) {
    /** The same motion with the movement taken out. Transitions still happen; they do not travel. */
    public fun reduced(): HarmonyMotionTokens = copy(
        instantMillis = 0,
        quickMillis = 0,
        standardMillis = 0,
        deliberateMillis = 0,
        celebrationMillis = 0,
    )
}

@Immutable
public data class HarmonyTypographyTokens(
    /** Read from a keyboard bench, several feet back. The largest thing on the screen. */
    val chordSymbol: TextUnit,
    val display: TextUnit,
    val heading: TextUnit,
    val title: TextUnit,
    val body: TextUnit,
    val label: TextUnit,
    val caption: TextUnit,
)

/**
 * The palette of the approved screens.
 *
 * Sampled from `interface/harmony_home_approved.jpg` and
 * `interface/assets/progression-run-background.png`; see the note on [HarmonyColorTokens].
 */
public val ApprovedColors: HarmonyColorTokens = HarmonyColorTokens(
    backgroundBase = Color(0xFF0A0908),
    backgroundSunken = Color(0xFF050403),
    surface = Color(0xFF1A1813),
    surfaceRaised = Color(0xFF23201B),
    surfaceOverlay = Color(0xFF2C2823),
    scrim = Color(0xCC050403),

    accentPrimary = Color(0xFFC98A3C),
    accentSecondary = Color(0xFFF0C090),
    onAccent = Color(0xFF120E08),

    textPrimary = Color(0xFFF8F0E8),
    textSecondary = Color(0xFFC0BCB2),
    textTertiary = Color(0xFF8F887D),
    outline = Color(0xFF4A4238),

    feedbackSuccess = Color(0xFF7FCB9B),
    feedbackWarning = Color(0xFFE8C25A),
    feedbackError = Color(0xFFE8837B),
    feedbackNeutral = Color(0xFFA9B0B6),

    staffLine = Color(0xFF8F887D),
    staffLedger = Color(0xFF8F887D),
    noteHead = Color(0xFFF8F0E8),
    accidental = Color(0xFFF0C090),
    clef = Color(0xFFF8F0E8),

    keyWhite = Color(0xFFEDE7DC),
    keyWhitePressed = Color(0xFFC9B189),
    keyBlack = Color(0xFF17150F),
    keyBlackPressed = Color(0xFF4A3A22),
    keyBorder = Color(0xFF0A0908),
    keyLabel = Color(0xFF2A2620),
    keyTarget = Color(0xFFC98A3C),

    gateLocked = Color(0xFF6A6156),
    gateAvailable = Color(0xFFC98A3C),
    gateInProgress = Color(0xFFF0C090),
    gateMastered = Color(0xFF7FCB9B),
)

public val DefaultSpacing: HarmonySpacingTokens = HarmonySpacingTokens(
    hairline = 1.dp,
    tight = 4.dp,
    small = 8.dp,
    medium = 16.dp,
    large = 24.dp,
    section = 40.dp,
    minimumTouchTarget = 56.dp,
)

public val DefaultShapes: HarmonyShapeTokens = HarmonyShapeTokens(
    radiusNone = 0.dp,
    radiusSmall = 4.dp,
    radiusMedium = 12.dp,
    radiusLarge = 20.dp,
    radiusPill = 999.dp,
    elevationFlat = 0.dp,
    elevationRaised = 2.dp,
    elevationFloating = 8.dp,
    elevationOverlay = 16.dp,
)

public val DefaultMotion: HarmonyMotionTokens = HarmonyMotionTokens(
    instantMillis = 0,
    quickMillis = 120,
    standardMillis = 220,
    deliberateMillis = 420,
    celebrationMillis = 900,
    standardEasing = FastOutSlowInEasing,
    emphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    exitEasing = LinearOutSlowInEasing,
)

public val DefaultTypography: HarmonyTypographyTokens = HarmonyTypographyTokens(
    chordSymbol = 64.sp,
    display = 40.sp,
    heading = 28.sp,
    title = 20.sp,
    body = 16.sp,
    label = 14.sp,
    caption = 13.sp,
)

internal val LocalHarmonyColors = staticCompositionLocalOf { ApprovedColors }
internal val LocalHarmonySpacing = staticCompositionLocalOf { DefaultSpacing }
internal val LocalHarmonyShapes = staticCompositionLocalOf { DefaultShapes }
internal val LocalHarmonyMotion = staticCompositionLocalOf { DefaultMotion }
internal val LocalHarmonyTypography = staticCompositionLocalOf { DefaultTypography }
