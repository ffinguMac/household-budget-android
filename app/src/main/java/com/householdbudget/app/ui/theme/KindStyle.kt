package com.householdbudget.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.householdbudget.app.domain.CategoryKind

// ──────────────────────────────────────────────────────────────────────────
// CategoryKind 별 색/부호 단일 결정 지점.
// 표기 규칙: INCOME "+"/secondary(초록), EXPENSE "−"/error(빨강), SAVINGS "↑"/primary(파랑)
// ──────────────────────────────────────────────────────────────────────────

/** 금액 강조색: INCOME=secondary, EXPENSE=error, SAVINGS=primary */
@Composable
fun kindAccent(kind: CategoryKind): Color =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.secondary
        CategoryKind.EXPENSE -> MaterialTheme.colorScheme.error
        CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primary
    }

/** 아바타/칩 배경: INCOME=secondaryContainer, EXPENSE=errorContainer, SAVINGS=primaryContainer */
@Composable
fun kindContainer(kind: CategoryKind): Color =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.secondaryContainer
        CategoryKind.EXPENSE -> MaterialTheme.colorScheme.errorContainer
        CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primaryContainer
    }

/** kindContainer 위의 전경색: 각각 onSecondaryContainer / onErrorContainer / onPrimaryContainer */
@Composable
fun kindOnContainer(kind: CategoryKind): Color =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.onSecondaryContainer
        CategoryKind.EXPENSE -> MaterialTheme.colorScheme.onErrorContainer
        CategoryKind.SAVINGS -> MaterialTheme.colorScheme.onPrimaryContainer
    }

/** 부호 접두사: INCOME="+", EXPENSE="−"(U+2212), SAVINGS="↑" */
fun kindSignPrefix(kind: CategoryKind): String =
    when (kind) {
        CategoryKind.INCOME -> "+"
        CategoryKind.EXPENSE -> "−"
        CategoryKind.SAVINGS -> "↑"
    }

// colorScheme 매핑이 바뀌어도 동작하도록 surface 밝기로 다크 여부를 판정한다.
@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** 홈 히어로 카드용 그라디언트. 라이트=TossBlue→TossBlueDark, 다크는 어둡게 눌러 대비 유지. */
@Composable
fun heroGradient(): Brush =
    if (isDarkSurface()) {
        Brush.linearGradient(colors = listOf(HeroGradientDarkStart, HeroGradientDarkEnd))
    } else {
        Brush.linearGradient(colors = listOf(TossBlue, TossBlueDark))
    }

/** 히어로 카드 위 텍스트 색 (그라디언트 위에서 항상 읽히는 색). */
@Composable
fun onHeroColor(): Color = PureWhite

/** 히어로 카드 위 보조 텍스트 색 (위 색의 낮은 알파). */
@Composable
fun onHeroMutedColor(): Color = onHeroColor().copy(alpha = 0.72f)
