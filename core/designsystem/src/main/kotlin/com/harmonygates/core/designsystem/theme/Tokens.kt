package com.harmonygates.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Design tokens.
 *
 * 12_UI_UX_AND_FIGMA_HANDOFF.md and non-negotiable rule 10: the real visual system is designed
 * in Figma *after* the functional specification, and Phase 12 applies it. Everything visual is
 * therefore addressed through a token here rather than a literal at the call site, so that
 * phase replaces values in this file instead of editing every screen.
 *
 * The values below are deliberately plain. They are placeholders, not a proposed look.
 */
@Immutable
public data class HarmonyColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    /**
     * Feedback colours.
     *
     * 18_ACCEPTANCE_CRITERIA.md: "Correctness is not represented by color alone." These exist
     * to reinforce a verdict that is already carried by an icon and by text.
     */
    val correct: Color,
    val incorrect: Color,
    val partial: Color,
    val awaitingInput: Color,
)

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

@Immutable
public data class HarmonyTypographyTokens(
    val chordSymbol: TextUnit,
    val heading: TextUnit,
    val body: TextUnit,
    val caption: TextUnit,
)

/** The placeholder palette used until the Figma system lands in Phase 12. */
public val PlaceholderDarkColors: HarmonyColorTokens = HarmonyColorTokens(
    background = Color(0xFF12131A),
    surface = Color(0xFF1B1D26),
    surfaceRaised = Color(0xFF262935),
    onSurface = Color(0xFFEDEEF2),
    onSurfaceMuted = Color(0xFF9BA0B0),
    accent = Color(0xFF7FA6FF),
    onAccent = Color(0xFF0B1020),
    outline = Color(0xFF3A3E4D),
    correct = Color(0xFF5FD08A),
    incorrect = Color(0xFFF08A8A),
    partial = Color(0xFFE5C55F),
    awaitingInput = Color(0xFF7FA6FF),
)

/** The placeholder light palette. */
public val PlaceholderLightColors: HarmonyColorTokens = HarmonyColorTokens(
    background = Color(0xFFF7F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEFF1F6),
    onSurface = Color(0xFF15161C),
    onSurfaceMuted = Color(0xFF5C6070),
    accent = Color(0xFF2F5BD0),
    onAccent = Color(0xFFFFFFFF),
    outline = Color(0xFFC9CDD9),
    correct = Color(0xFF1F7A46),
    incorrect = Color(0xFFA32B2B),
    partial = Color(0xFF8A6B12),
    awaitingInput = Color(0xFF2F5BD0),
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

public val DefaultTypography: HarmonyTypographyTokens = HarmonyTypographyTokens(
    chordSymbol = 64.sp,
    heading = 24.sp,
    body = 16.sp,
    caption = 13.sp,
)

internal val LocalHarmonyColors = staticCompositionLocalOf { PlaceholderDarkColors }
internal val LocalHarmonySpacing = staticCompositionLocalOf { DefaultSpacing }
internal val LocalHarmonyTypography = staticCompositionLocalOf { DefaultTypography }
