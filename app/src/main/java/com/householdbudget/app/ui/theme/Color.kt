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

// ── Hero card gradient (dark theme) ─────────────────────────────────────────
// 다크에서는 라이트용 TossBlue 그라디언트가 너무 밝아 흰 텍스트 대비가 약해진다.
// 어둡게 누른 블루 두 단계 — 흰 텍스트가 항상 읽히는 범위.
val HeroGradientDarkStart = Color(0xFF2A5CB8)
val HeroGradientDarkEnd = Color(0xFF1B3F86)

// ──────────────────────────────────────────────────────────────────────────
// Midnight palette (라운드 2) — 다크 단일 테마.
// 배경 #0C1018 + 민트 #5EE8B5 포인트, 지출 레드 #FF7D8A, 저축 블루 #7FB2FF.
// ──────────────────────────────────────────────────────────────────────────

// Neutrals
val MidnightBg = Color(0xFF0C1018) // background
val MidnightSurface = Color(0xFF131A26) // surface
val MidnightBorder = Color(0xFF1C2634) // outlineVariant (서피스 보더)
val MidnightSurfaceHigh = Color(0xFF1A2434) // surfaceVariant / surfaceContainerHigh
val MidnightSurfaceLow = Color(0xFF101623) // surfaceContainerLow
val MidnightSurfaceHighest = Color(0xFF222D40) // surfaceContainerHighest
val MidnightOnSurface = Color(0xFFEDF1F7) // 본문
val MidnightMuted = Color(0xFF7D8AA0) // 보조 텍스트
val MidnightOutline = Color(0xFF2A3648) // outline (보더보다 한 단계 밝게)

// Brand — mint
val MintPrimary = Color(0xFF5EE8B5)
val OnMint = Color(0xFF06281A)
val MintContainer = Color(0xFF12352A)
val OnMintContainer = Color(0xFFA7F3D3)
val MintInverse = Color(0xFF0E8C5C) // 밝은 inverseSurface 위에서 쓰는 어두운 민트

// Semantic — expense red
val ExpenseRed = Color(0xFFFF7D8A)
val OnExpenseRed = Color(0xFF360911)
val ExpenseRedContainer = Color(0xFF3D1620)
val OnExpenseRedContainer = Color(0xFFFFB3BA)

// Semantic — savings blue
val SavingsBlue = Color(0xFF7FB2FF)
val OnSavingsBlue = Color(0xFF071A33)
val SavingsBlueContainer = Color(0xFF16294A)
val OnSavingsBlueContainer = Color(0xFFBDD7FF)

// Hero card gradient — 미드나잇 전용
val MidnightHeroStart = Color(0xFF131A26)
val MidnightHeroEnd = Color(0xFF0F2231)
