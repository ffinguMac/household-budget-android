package com.householdbudget.app.ui.feed

import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.domain.CategoryKind

/** 피드의 하루 묶음. 종류별 합계는 묶을 때 한 번만 계산한다. */
data class FeedDay(
    val epochDay: Long,
    val rows: List<TransactionWithCategoryRow>,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val savingsMinor: Long,
) {
    fun totalOf(kind: CategoryKind): Long =
        when (kind) {
            CategoryKind.INCOME -> incomeMinor
            CategoryKind.EXPENSE -> expenseMinor
            CategoryKind.SAVINGS -> savingsMinor
        }
}

/** 날짜 내림차순. 하루 안의 행 순서는 입력 순서 그대로. */
fun groupFeedDays(rows: List<TransactionWithCategoryRow>): List<FeedDay> =
    rows.groupBy { it.occurredEpochDay }
        .entries
        .sortedByDescending { it.key }
        .map { (day, dayRows) ->
            fun sum(kind: CategoryKind) = dayRows.filter { it.kind == kind.storage }.sumOf { it.amountMinor }
            FeedDay(day, dayRows, sum(CategoryKind.INCOME), sum(CategoryKind.EXPENSE), sum(CategoryKind.SAVINGS))
        }

/** 새 기간이 더 미래면 +1(오른쪽에서 들어옴), 과거면 -1, 처음이거나 같으면 0(애니메이션 없음). */
fun slideDirection(previousStartEpochDay: Long?, newStartEpochDay: Long?): Int =
    if (previousStartEpochDay == null || newStartEpochDay == null) 0
    else newStartEpochDay.compareTo(previousStartEpochDay).coerceIn(-1, 1)

/** 홈 히어로의 예산 상태. [remainingMinor] 가 음수면 초과. */
data class BudgetStatus(val remainingMinor: Long, val dailyAllowanceMinor: Long, val usedFraction: Float)

/** 하루 여유는 남은 날(오늘 포함, 최소 1)로 나눠 100원 단위로 내린다. 예산 0 이하는 썼으면 100% 사용. */
fun budgetStatus(budgetMinor: Long, spentMinor: Long, daysRemaining: Int): BudgetStatus {
    val remaining = budgetMinor - spentMinor
    val daily = remaining.coerceAtLeast(0L) / daysRemaining.coerceAtLeast(1) / 100 * 100
    val used =
        if (budgetMinor <= 0L) (if (spentMinor > 0L) 1f else 0f)
        else (spentMinor.toDouble() / budgetMinor).toFloat().coerceIn(0f, 1f)
    return BudgetStatus(remaining, daily, used)
}
