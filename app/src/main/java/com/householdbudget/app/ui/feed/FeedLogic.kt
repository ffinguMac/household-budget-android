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
