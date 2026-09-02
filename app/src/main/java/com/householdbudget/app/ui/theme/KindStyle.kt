package com.householdbudget.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.householdbudget.app.domain.CategoryKind

// ──────────────────────────────────────────────────────────────────────────
// CategoryKind 별 색/부호 단일 결정 지점.
// 표기 규칙: INCOME "+"/secondary(민트), EXPENSE "−"/error(레드), SAVINGS "↑"/tertiary(블루)
// ──────────────────────────────────────────────────────────────────────────

/** 금액 강조색: INCOME=secondary, EXPENSE=error, SAVINGS=tertiary */
@Composable
fun kindAccent(kind: CategoryKind): Color =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.secondary
        CategoryKind.EXPENSE -> MaterialTheme.colorScheme.error
        CategoryKind.SAVINGS -> MaterialTheme.colorScheme.tertiary
    }

/** 아바타/칩 배경: INCOME=secondaryContainer, EXPENSE=errorContainer, SAVINGS=tertiaryContainer */
@Composable
fun kindContainer(kind: CategoryKind): Color =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.secondaryContainer
        CategoryKind.EXPENSE -> MaterialTheme.colorScheme.errorContainer
        CategoryKind.SAVINGS -> MaterialTheme.colorScheme.tertiaryContainer
    }

/** kindContainer 위의 전경색: 각각 onSecondaryContainer / onErrorContainer / onTertiaryContainer */
@Composable
fun kindOnContainer(kind: CategoryKind): Color =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.onSecondaryContainer
        CategoryKind.EXPENSE -> MaterialTheme.colorScheme.onErrorContainer
        CategoryKind.SAVINGS -> MaterialTheme.colorScheme.onTertiaryContainer
    }

/** 부호 접두사: INCOME="+", EXPENSE="−"(U+2212), SAVINGS="↑" */
fun kindSignPrefix(kind: CategoryKind): String =
    when (kind) {
        CategoryKind.INCOME -> "+"
        CategoryKind.EXPENSE -> "−"
        CategoryKind.SAVINGS -> "↑"
    }

/** 히어로 카드용 그라디언트 — 미드나잇 전용(#131A26 → #0F2231). 보더는 호출자가 그린다. */
@Composable
fun heroGradient(): Brush =
    Brush.linearGradient(colors = listOf(MidnightHeroStart, MidnightHeroEnd))

/** 히어로 카드 위 본문 텍스트 색. 금액 강조는 호출자가 [kindAccent] 로. */
@Composable
fun onHeroColor(): Color = MidnightOnSurface

/** 히어로 카드 위 보조 텍스트 색 (위 색의 낮은 알파). */
@Composable
fun onHeroMutedColor(): Color = onHeroColor().copy(alpha = 0.72f)
