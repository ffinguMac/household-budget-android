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
        primary = NavyDeep,
        onPrimary = Color.White,
        primaryContainer = NavyContainer,
        onPrimaryContainer = NavyFixed,
        secondary = TealGreen,
        onSecondary = Color.White,
        secondaryContainer = TealContainer,
        onSecondaryContainer = OnTealContainer,
        tertiary = RustDark,
        onTertiary = Color.White,
        tertiaryContainer = RustContainer,
        onTertiaryContainer = RustFixed,
        error = ErrorRed,
        onError = Color.White,
        errorContainer = ErrorContainerColor,
        onErrorContainer = OnErrorContainerColor,
        background = BackgroundLight,
        onBackground = OnSurfaceColor,
        surface = SurfaceLowest,
        onSurface = OnSurfaceColor,
        surfaceVariant = SurfaceHighest,
        onSurfaceVariant = OnSurfaceVarColor,
        outline = OutlineColor,
        outlineVariant = OutlineVarColor,
        inverseSurface = InverseSurfaceColor,
        inverseOnSurface = InverseOnSurfaceColor,
        inversePrimary = NavyFixedDim,
        scrim = Color.Black,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = NavyFixed,
        onPrimary = Color(0xFF091D2E),
        primaryContainer = Color(0xFF36485B),
        onPrimaryContainer = NavyFixed,
        secondary = TealFixedDim,
        onSecondary = OnTealFixed,
        secondaryContainer = Color(0xFF005143),
        onSecondaryContainer = TealFixed,
        tertiary = RustFixedDim,
        onTertiary = OnRustFixed,
        tertiaryContainer = RustContainer,
        onTertiaryContainer = RustFixed,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF191C1D),
        onBackground = Color(0xFFE1E3E4),
        surface = Color(0xFF191C1D),
        onSurface = Color(0xFFE1E3E4),
        surfaceVariant = Color(0xFF43474C),
        onSurfaceVariant = OutlineVarColor,
        outline = OutlineColor,
        outlineVariant = Color(0xFF43474C),
        inverseSurface = Color(0xFFE1E3E4),
        inverseOnSurface = Color(0xFF2E3132),
        inversePrimary = NavyDeep,
        scrim = Color.Black,
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
