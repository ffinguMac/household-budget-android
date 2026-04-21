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
        primary = Teal700,
        onPrimary = Color.White,
        primaryContainer = Teal100,
        onPrimaryContainer = Teal950,
        secondary = Sage700,
        onSecondary = Color.White,
        secondaryContainer = Mist200,
        onSecondaryContainer = Teal950,
        tertiary = Amber700,
        onTertiary = Color.White,
        tertiaryContainer = Amber100,
        onTertiaryContainer = Teal950,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Cream100,
        onBackground = Teal950,
        surface = Cream50,
        onSurface = Teal950,
        surfaceVariant = Mist200,
        onSurfaceVariant = Color(0xFF3F4948),
        outline = Mist300,
        outlineVariant = Color(0xFFBFC9C7),
        scrim = Color(0xFF000000),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = TealDarkPrimary,
        onPrimary = Teal950,
        primaryContainer = TealDarkContainer,
        onPrimaryContainer = Teal100,
        secondary = Color(0xFFAAC7BB),
        onSecondary = Color(0xFF0A1F16),
        secondaryContainer = Color(0xFF355342),
        onSecondaryContainer = Color(0xFFD6EADD),
        tertiary = Color(0xFFFFB86C),
        onTertiary = Color(0xFF4A2800),
        tertiaryContainer = Color(0xFF6B3D00),
        onTertiaryContainer = Color(0xFFFFDDB8),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Night950,
        onBackground = Color(0xFFE0E3E2),
        surface = Night900,
        onSurface = Color(0xFFE0E3E2),
        surfaceVariant = Night700,
        onSurfaceVariant = Color(0xFFBFC9C7),
        outline = Color(0xFF899392),
        outlineVariant = Night800,
        scrim = Color(0xFF000000),
    )

@Composable
fun HouseholdBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 고정 브랜드 색을 쓰려면 false 권장 */
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
