package com.harmonygates.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's theme.
 *
 * Features read tokens through [HarmonyTheme] rather than from `MaterialTheme` directly, so the
 * Figma system can be swapped in behind this façade without touching a screen. Material 3 is
 * still configured underneath because the Material components the app uses need a scheme to
 * draw with.
 *
 * **One palette, not two.** The approved screens in `interface/` are painted plates in a dark,
 * warm world, and a Compose layer that turned pale because the tablet was in light mode would
 * be drawn on top of artwork that did not turn with it. So there is no light theme to switch
 * to; contrast is met inside the dark palette instead, which `TokenTest` checks.
 *
 * @param reducedMotion collapses every duration to zero. 12 §10 asks for the mode; passing the
 *   system's own accessibility preference in is the app's job, because reading it needs a
 *   context and this module deliberately has no Android dependencies beyond Compose.
 */
@Composable
public fun HarmonyTheme(
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHarmonyColors provides ApprovedColors,
        LocalHarmonySpacing provides DefaultSpacing,
        LocalHarmonyShapes provides DefaultShapes,
        LocalHarmonyMotion provides if (reducedMotion) DefaultMotion.reduced() else DefaultMotion,
        LocalHarmonyTypography provides DefaultTypography,
    ) {
        MaterialTheme(
            colorScheme = ApprovedColors.toMaterialScheme(),
            typography = materialTypography(),
            content = content,
        )
    }
}

/** Token accessor. `HarmonyTheme.colors.accentPrimary` rather than a hard-coded hex anywhere. */
public object HarmonyTheme {
    public val colors: HarmonyColorTokens
        @Composable @ReadOnlyComposable get() = LocalHarmonyColors.current

    public val spacing: HarmonySpacingTokens
        @Composable @ReadOnlyComposable get() = LocalHarmonySpacing.current

    public val shapes: HarmonyShapeTokens
        @Composable @ReadOnlyComposable get() = LocalHarmonyShapes.current

    public val motion: HarmonyMotionTokens
        @Composable @ReadOnlyComposable get() = LocalHarmonyMotion.current

    public val typography: HarmonyTypographyTokens
        @Composable @ReadOnlyComposable get() = LocalHarmonyTypography.current
}

private fun HarmonyColorTokens.toMaterialScheme() = darkColorScheme(
    primary = accentPrimary,
    onPrimary = onAccent,
    secondary = accentSecondary,
    onSecondary = onAccent,
    background = backgroundBase,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceRaised,
    onSurfaceVariant = textSecondary,
    outline = outline,
    scrim = scrim,
    error = feedbackError,
)

@Composable
private fun materialTypography(): Typography {
    val base = MaterialTheme.typography
    return base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold),
        // The chord symbol is read from across a keyboard, so it gets its own scale token
        // rather than borrowing a Material role.
        headlineLarge = TextStyle(
            fontSize = DefaultTypography.chordSymbol,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}
