package com.harmonygates.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's theme.
 *
 * Features read tokens through [HarmonyTheme] rather than from `MaterialTheme` directly, so
 * that Phase 12 can swap the Figma system in behind this façade without touching a screen.
 * Material 3 is still configured underneath because the Material components the app uses need
 * a scheme to draw with.
 */
@Composable
public fun HarmonyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) PlaceholderDarkColors else PlaceholderLightColors

    CompositionLocalProvider(
        LocalHarmonyColors provides colors,
        LocalHarmonySpacing provides DefaultSpacing,
        LocalHarmonyTypography provides DefaultTypography,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(darkTheme),
            typography = materialTypography(),
            content = content,
        )
    }
}

/** Token accessor. `HarmonyTheme.colors.accent` rather than a hard-coded hex anywhere. */
public object HarmonyTheme {
    public val colors: HarmonyColorTokens
        @Composable @ReadOnlyComposable get() = LocalHarmonyColors.current

    public val spacing: HarmonySpacingTokens
        @Composable @ReadOnlyComposable get() = LocalHarmonySpacing.current

    public val typography: HarmonyTypographyTokens
        @Composable @ReadOnlyComposable get() = LocalHarmonyTypography.current
}

private fun HarmonyColorTokens.toMaterialScheme(darkTheme: Boolean) =
    if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = this.onSurface,
            surfaceVariant = surfaceRaised,
            onSurfaceVariant = onSurfaceMuted,
            outline = outline,
            error = incorrect,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = this.onSurface,
            surfaceVariant = surfaceRaised,
            onSurfaceVariant = onSurfaceMuted,
            outline = outline,
            error = incorrect,
        )
    }

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
