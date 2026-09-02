package com.householdbudget.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────────────────────────────────────
// 미드나잇 — 다크 단일 테마. 시스템 라이트/다크 설정과 무관하게 항상 이 팔레트.
// primary/secondary=민트, error=지출 레드, tertiary=저축 블루.
// ──────────────────────────────────────────────────────────────────────────
private val MidnightColorScheme =
    darkColorScheme(
        primary = MintPrimary,
        onPrimary = OnMint,
        primaryContainer = MintContainer,
        onPrimaryContainer = OnMintContainer,
        secondary = MintPrimary,
        onSecondary = OnMint,
        secondaryContainer = MintContainer,
        onSecondaryContainer = OnMintContainer,
        tertiary = SavingsBlue,
        onTertiary = OnSavingsBlue,
        tertiaryContainer = SavingsBlueContainer,
        onTertiaryContainer = OnSavingsBlueContainer,
        error = ExpenseRed,
        onError = OnExpenseRed,
        errorContainer = ExpenseRedContainer,
        onErrorContainer = OnExpenseRedContainer,
        background = MidnightBg,
        onBackground = MidnightOnSurface,
        surface = MidnightSurface,
        onSurface = MidnightOnSurface,
        surfaceVariant = MidnightSurfaceHigh,
        onSurfaceVariant = MidnightMuted,
        surfaceContainerLowest = MidnightBg,
        surfaceContainerLow = MidnightSurfaceLow,
        surfaceContainer = MidnightSurface,
        surfaceContainerHigh = MidnightSurfaceHigh,
        surfaceContainerHighest = MidnightSurfaceHighest,
        outline = MidnightOutline,
        outlineVariant = MidnightBorder,
        inverseSurface = MidnightOnSurface,
        inverseOnSurface = MidnightSurface,
        inversePrimary = MintInverse,
        scrim = Color.Black,
    )

@Composable
fun HouseholdBudgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MidnightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
