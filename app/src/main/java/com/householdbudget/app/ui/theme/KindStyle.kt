package com.householdbudget.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.householdbudget.app.domain.CategoryKind

// ──────────────────────────────────────────────────────────────────────────
// CategoryKind 별 색/부호 단일 결정 지점.
// 표기 규칙: INCOME "+"/primary, EXPENSE "−"/onSurface, SAVINGS "↑"/onSurface.
// error는 예산 초과·삭제·오류에만 쓴다. 저축은 점(tertiary)으로만 구분한다.
// ──────────────────────────────────────────────────────────────────────────

/** 금액 색: INCOME=primary, EXPENSE·SAVINGS=onSurface */
@Composable
fun kindAccent(kind: CategoryKind): Color =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.primary
        CategoryKind.EXPENSE, CategoryKind.SAVINGS -> MaterialTheme.colorScheme.onSurface
    }

/** 종류 점 색: INCOME=primary, SAVINGS=tertiary, EXPENSE=점 없음(null) */
@Composable
fun kindDotColor(kind: CategoryKind): Color? =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.primary
        CategoryKind.SAVINGS -> MaterialTheme.colorScheme.tertiary
        CategoryKind.EXPENSE -> null
    }

/** 아바타/칩 배경: INCOME=secondaryContainer, EXPENSE·SAVINGS=surfaceVariant */
@Composable
fun kindContainer(kind: CategoryKind): Color =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.secondaryContainer
        CategoryKind.EXPENSE, CategoryKind.SAVINGS -> MaterialTheme.colorScheme.surfaceVariant
    }

/** kindContainer 위의 전경색: INCOME=onSecondaryContainer, EXPENSE·SAVINGS=onSurface */
@Composable
fun kindOnContainer(kind: CategoryKind): Color =
    when (kind) {
        CategoryKind.INCOME -> MaterialTheme.colorScheme.onSecondaryContainer
        CategoryKind.EXPENSE, CategoryKind.SAVINGS -> MaterialTheme.colorScheme.onSurface
    }

/** 부호 접두사: INCOME="+", EXPENSE="−"(U+2212), SAVINGS="↑" */
fun kindSignPrefix(kind: CategoryKind): String =
    when (kind) {
        CategoryKind.INCOME -> "+"
        CategoryKind.EXPENSE -> "−"
        CategoryKind.SAVINGS -> "↑"
    }
