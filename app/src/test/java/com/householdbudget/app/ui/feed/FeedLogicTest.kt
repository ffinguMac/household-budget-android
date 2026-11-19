package com.householdbudget.app.ui.feed

import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.domain.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedLogicTest {
    private fun row(id: Long, day: Long, amount: Long, kind: String) =
        TransactionWithCategoryRow(id, day, amount, kind, 1, "c", 1, "p", "", "MANUAL")

    @Test fun groupFeedDays_sortsDaysDescending_andKeepsRowOrder() {
        val days = groupFeedDays(listOf(row(3, 100, 1_000, "EXPENSE"), row(2, 101, 2_000, "EXPENSE"), row(1, 100, 500, "INCOME")))
        assertEquals(listOf(101L, 100L), days.map { it.epochDay })
        assertEquals(listOf(3L, 1L), days[1].rows.map { it.id })
    }

    @Test fun groupFeedDays_computesPerKindTotalsOnce() {
        val day = groupFeedDays(listOf(row(1, 100, 1_000, "EXPENSE"), row(2, 100, 2_500, "EXPENSE"), row(3, 100, 9_000, "INCOME"))).single()
        assertEquals(3_500L, day.expenseMinor); assertEquals(9_000L, day.incomeMinor); assertEquals(0L, day.savingsMinor)
        assertEquals(3_500L, day.totalOf(CategoryKind.EXPENSE))
    }

    @Test fun groupFeedDays_empty() = assertEquals(emptyList<FeedDay>(), groupFeedDays(emptyList()))

    @Test fun slideDirection_olderPeriodEntersFromLeft() = assertEquals(-1, slideDirection(200, 170))

    @Test fun slideDirection_newerPeriodEntersFromRight() = assertEquals(1, slideDirection(170, 200))

    @Test fun slideDirection_jumpBackToTodayAcrossSeveralPeriods() = assertEquals(1, slideDirection(80, 200))

    @Test fun slideDirection_firstLoadOrSamePeriod_isZero() {
        assertEquals(0, slideDirection(null, 200)); assertEquals(0, slideDirection(200, null)); assertEquals(0, slideDirection(200, 200))
    }
}
