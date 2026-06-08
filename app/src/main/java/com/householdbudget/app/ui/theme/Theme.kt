package com.householdbudget.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
    lightColorScheme(
        primary = TossBlue,
        onPrimary = PureWhite,
        primaryContainer = TossBlueContainer,
        onPrimaryContainer = TossBlueContainerText,
        secondary = TossGreen,
        onSecondary = PureWhite,
        secondaryContainer = TossGreenContainer,
        onSecondaryContainer = TossGreenText,
        tertiary = TossBlueDark,
        onTertiary = PureWhite,
        tertiaryContainer = TossBlueContainer,
        onTertiaryContainer = TossBlueContainerText,
        error = ErrorRed,
        onError = PureWhite,
        errorContainer = ErrorContainerColor,
        onErrorContainer = OnErrorContainerColor,
        background = Gray50,
        onBackground = Gray900,
        surface = PureWhite,
        onSurface = Gray900,
        surfaceVariant = Gray100,
        onSurfaceVariant = Gray500,
        surfaceContainerLowest = PureWhite,
        surfaceContainerLow = Gray50,
        surfaceContainer = Gray100,
        surfaceContainerHigh = Gray100,
        surfaceContainerHighest = Gray200,
        outline = Gray300,
        outlineVariant = Gray200,
        inverseSurface = Gray900,
        inverseOnSurface = Gray50,
        inversePrimary = TossBlueContainer,
        scrim = Color.Black,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = TossBlueDarkTheme,
        onPrimary = Color(0xFF0A1A30),
        primaryContainer = TossBlueContainerDark,
        onPrimaryContainer = Color(0xFFCFE2FF),
        secondary = Color(0xFF3FD693),
        onSecondary = Color(0xFF00351F),
        secondaryContainer = Color(0xFF105437),
        onSecondaryContainer = Color(0xFFB9F2D5),
        tertiary = TossBlueDarkTheme,
        onTertiary = Color(0xFF0A1A30),
        tertiaryContainer = TossBlueContainerDark,
        onTertiaryContainer = Color(0xFFCFE2FF),
        error = Color(0xFFFF8A93),
        onError = Color(0xFF5A0009),
        errorContainer = Color(0xFF7A1822),
        onErrorContainer = Color(0xFFFFDADD),
        background = DarkBg,
        onBackground = DarkOnSurface,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceContainerLowest = DarkBg,
        surfaceContainerLow = DarkSurface,
        surfaceContainer = DarkSurfaceVariant,
        surfaceContainerHigh = DarkSurfaceHigh,
        surfaceContainerHighest = Color(0xFF3C3E47),
        outline = DarkOutline,
        outlineVariant = DarkOutline,
        inverseSurface = DarkOnSurface,
        inverseOnSurface = DarkSurface,
        inversePrimary = TossBlue,
        scrim = Color.Black,
    )

@Composable
fun HouseholdBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Toss uses its own brand palette — never follow Material You dynamic color.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
