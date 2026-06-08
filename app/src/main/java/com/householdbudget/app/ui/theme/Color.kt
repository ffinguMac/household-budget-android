package com.householdbudget.app.ui.theme

import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────────────────────────────────────
// Toss-style design palette
// Brand: Toss Blue. Neutrals: cool gray scale. Semantic: blue(income via green),
// red(expense), blue(savings). Everything flat, high-contrast, lots of white.
// ──────────────────────────────────────────────────────────────────────────

// Brand – Toss Blue
val TossBlue = Color(0xFF3182F6)
val TossBlueDark = Color(0xFF1B64DA)
val TossBluePressed = Color(0xFF1957C2)
val TossBlueContainer = Color(0xFFE8F3FF)
val TossBlueContainerText = Color(0xFF1B64DA)

// Semantic – Income (mint/green)
val TossGreen = Color(0xFF15B86F)
val TossGreenContainer = Color(0xFFE7F9F0)
val TossGreenText = Color(0xFF0E8C53)

// Semantic – Expense (red)
val TossRed = Color(0xFFF04452)
val TossRedContainer = Color(0xFFFFEBEC)
val TossRedText = Color(0xFFD32F3C)

// Gray scale (Toss greyscale)
val Gray900 = Color(0xFF191F28) // primary text
val Gray800 = Color(0xFF333D4B)
val Gray700 = Color(0xFF4E5968)
val Gray600 = Color(0xFF6B7684) // secondary text strong
val Gray500 = Color(0xFF8B95A1) // secondary text
val Gray400 = Color(0xFFB0B8C1) // disabled / inactive icon
val Gray300 = Color(0xFFD1D6DB)
val Gray200 = Color(0xFFE5E8EB) // dividers / outline
val Gray100 = Color(0xFFF2F4F6) // card / surface variant
val Gray50 = Color(0xFFF9FAFB) // app background
val PureWhite = Color(0xFFFFFFFF)

// Error
val ErrorRed = TossRed
val ErrorContainerColor = TossRedContainer
val OnErrorContainerColor = TossRedText

// ── Dark theme neutrals ─────────────────────────────────────────────────────
val DarkBg = Color(0xFF17171C)
val DarkSurface = Color(0xFF1E1F25)
val DarkSurfaceVariant = Color(0xFF2A2C33)
val DarkSurfaceHigh = Color(0xFF32343C)
val DarkOnSurface = Color(0xFFEDEFF2)
val DarkOnSurfaceVariant = Color(0xFF9DA4AE)
val DarkOutline = Color(0xFF3A3D45)
val TossBlueDarkTheme = Color(0xFF5B9DFF)
val TossBlueContainerDark = Color(0xFF1F3A5C)
