package com.householdbudget.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightBlueScheme =
    lightColorScheme(
        background = LightBg,
        surfaceContainerLowest = LightBg,
        surface = LightSurface,
        surfaceContainerLow = LightSurface,
        surfaceContainer = LightSurface,
        inverseOnSurface = LightSurface,
        surfaceVariant = LightRaised,
        surfaceContainerHigh = LightRaised,
        surfaceContainerHighest = LightRaised,
        tertiaryContainer = LightRaised,
        errorContainer = LightRaised,
        outlineVariant = LightDivider,
        outline = LightOutline,
        onBackground = LightText,
        onSurface = LightText,
        inverseSurface = LightText,
        onSurfaceVariant = LightTextSecondary,
        primary = LightAccent,
        secondary = LightAccent,
        surfaceTint = LightAccent,
        onPrimary = LightOnAccent,
        onSecondary = LightOnAccent,
        primaryContainer = LightAccentSoft,
        secondaryContainer = LightAccentSoft,
        onPrimaryContainer = LightOnAccentSoft,
        onSecondaryContainer = LightOnAccentSoft,
        tertiary = LightSavings,
        onTertiaryContainer = LightSavings,
        error = LightDanger,
        onErrorContainer = LightDanger,
        onTertiary = LightOnAccent,
        onError = LightOnAccent,
        inversePrimary = DarkAccent,
        scrim = Color.Black,
    )

private val DarkBlueScheme =
    darkColorScheme(
        background = DarkBg,
        surfaceContainerLowest = DarkBg,
        surface = DarkSurface,
        surfaceContainerLow = DarkSurface,
        surfaceContainer = DarkSurface,
        inverseOnSurface = DarkSurface,
        surfaceVariant = DarkRaised,
        surfaceContainerHigh = DarkRaised,
        surfaceContainerHighest = DarkRaised,
        tertiaryContainer = DarkRaised,
        errorContainer = DarkRaised,
        outlineVariant = DarkDivider,
        outline = DarkOutline,
        onBackground = DarkText,
        onSurface = DarkText,
        inverseSurface = DarkText,
        onSurfaceVariant = DarkTextSecondary,
        primary = DarkAccent,
        secondary = DarkAccent,
        surfaceTint = DarkAccent,
        onPrimary = DarkOnAccent,
        onSecondary = DarkOnAccent,
        primaryContainer = DarkAccentSoft,
        secondaryContainer = DarkAccentSoft,
        onPrimaryContainer = DarkOnAccentSoft,
        onSecondaryContainer = DarkOnAccentSoft,
        tertiary = DarkSavings,
        onTertiaryContainer = DarkSavings,
        error = DarkDanger,
        onErrorContainer = DarkDanger,
        onTertiary = DarkOnAccent,
        onError = DarkOnAccent,
        inversePrimary = LightAccent,
        scrim = Color.Black,
    )

/** 시스템 다크모드는 따르지 않는다 — 사용자가 고른 테마(dark)가 우선. */
@Composable
fun HouseholdBudgetTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkBlueScheme else LightBlueScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
