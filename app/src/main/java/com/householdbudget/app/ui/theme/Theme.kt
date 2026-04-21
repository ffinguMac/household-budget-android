package com.householdbudget.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = Terracotta,
        onPrimary = Ivory,
        primaryContainer = TerracottaContainer,
        onPrimaryContainer = OnTerracottaContainer,
        secondary = SageGreen,
        onSecondary = Ivory,
        secondaryContainer = SageContainer,
        onSecondaryContainer = OnSageContainer,
        tertiary = OliveGray,
        onTertiary = Ivory,
        tertiaryContainer = WarmSand,
        onTertiaryContainer = NearBlack,
        error = ErrorCrimson,
        onError = Ivory,
        errorContainer = ErrorContainer,
        onErrorContainer = OnErrorContainer,
        background = Parchment,
        onBackground = NearBlack,
        surface = Ivory,
        onSurface = NearBlack,
        surfaceVariant = WarmSand,
        onSurfaceVariant = OliveGray,
        outline = BorderWarm,
        outlineVariant = BorderCream,
        scrim = Color(0xFF000000),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = CoralDark,
        onPrimary = DeepDark,
        primaryContainer = Color(0xFF6b3421),
        onPrimaryContainer = TerracottaContainer,
        secondary = SageDark,
        onSecondary = DeepDark,
        secondaryContainer = SageDarkContainer,
        onSecondaryContainer = Color(0xFFd6eadd),
        tertiary = WarmSilver,
        onTertiary = DeepDark,
        tertiaryContainer = DarkWarm,
        onTertiaryContainer = WarmSilver,
        error = Color(0xFFf08080),
        onError = Color(0xFF4a0f0f),
        errorContainer = Color(0xFF6b1515),
        onErrorContainer = Color(0xFFf5e0e0),
        background = DeepDark,
        onBackground = WarmSilver,
        surface = DarkSurface,
        onSurface = Color(0xFFe8e6dc),
        surfaceVariant = DarkWarm,
        onSurfaceVariant = WarmSilver,
        outline = OliveGray,
        outlineVariant = DarkWarm,
        scrim = Color(0xFF000000),
    )

@Composable
fun HouseholdBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
