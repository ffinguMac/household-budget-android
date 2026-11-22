package com.householdbudget.app.ui.stats

import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLogicTest {
    private val parents = listOf(
        ParentSpend(1, "식비", 480_000, 420_000), ParentSpend(2, "쇼핑", 208_000, 150_000),
        ParentSpend(3, "유동 지출", 150_000, 150_000), ParentSpend(4, "생활", 129_000, 140_000),
        ParentSpend(5, "여가", 106_000, 60_000), ParentSpend(6, "교통", 99_000, 120_000),
        ParentSpend(7, "기타", 22_000, 30_000),
    )

    @Test fun delta_roundsAndTreatsWithin5PercentInclusiveAsFlat() {
        assertEquals(14, deltaPercent(480_000, 420_000))
        assertEquals(-8, deltaPercent(129_000, 140_000))
        assertNull(deltaPercent(105_000, 100_000))
        assertNull(deltaPercent(95_000, 100_000))
        assertEquals(6, deltaPercent(106_000, 100_000))
        assertEquals(-6, deltaPercent(94_000, 100_000))
        assertNull(deltaPercent(104_000, 100_000))
        assertNull(deltaPercent(96_000, 100_000))
        assertNull(deltaPercent(150_000, 150_000))
    }

    @Test fun delta_previousZero_isFlat() {
        assertNull(deltaPercent(50_000, 0)); assertNull(deltaPercent(0, 0)); assertNull(deltaPercent(50_000, -1))
    }

    @Test fun summary_normalMonth() {
        val s = MapSummary(incomeMinor = 3_000_000, spentMinor = 1_194_000, savingsMinor = 700_000)
        assertTrue(s.hasIncome); assertEquals(40, s.spentPercent)
        assertEquals(1_106_000L, s.leftoverMinor); assertFalse(s.isOver)
        assertEquals(
            listOf(BarSegment(BarPart.SPENT, 1_194_000), BarSegment(BarPart.SAVINGS, 700_000), BarSegment(BarPart.LEFTOVER, 1_106_000)),
            s.barSegments(),
        )
    }

    @Test fun noIncome_hidesRatioAndBar() {
        val s = MapSummary(incomeMinor = 0, spentMinor = 300_000, savingsMinor = 100_000)
        assertFalse(s.hasIncome); assertEquals(0, s.spentPercent); assertTrue(s.barSegments().isEmpty())
    }

    @Test fun overIncome_showsOverSegment() {
        val s = MapSummary(incomeMinor = 1_000_000, spentMinor = 900_000, savingsMinor = 300_000)
        assertTrue(s.isOver); assertEquals(-200_000L, s.leftoverMinor); assertEquals(90, s.spentPercent)
        assertEquals(
            listOf(BarSegment(BarPart.SPENT, 700_000), BarSegment(BarPart.SAVINGS, 300_000), BarSegment(BarPart.OVER, 200_000)),
            s.barSegments(),
        )
        assertTrue(s.barSegments().all { it.weightMinor > 0 })
    }

    @Test fun zeroParts_areDropped() {
        assertEquals(listOf(BarPart.SPENT, BarPart.LEFTOVER),
            MapSummary(1_000_000, 400_000, 0).barSegments().map { it.part })
        assertEquals(listOf(BarPart.SPENT, BarPart.SAVINGS),
            MapSummary(1_000_000, 600_000, 400_000).barSegments().map { it.part })
        assertEquals(listOf(BarPart.LEFTOVER), MapSummary(1_000_000, 0, 0).barSegments().map { it.part })
    }

    @Test fun rows_sortedWithShareDeltaAndBar() {
        val rows = buildMapRows(parents.shuffled(java.util.Random(1)))
        assertEquals(listOf("식비", "쇼핑", "유동 지출", "생활", "여가", "교통", "기타"), rows.map { it.name })
        assertEquals(listOf(40, 17, 13, 11, 9, 8, 2), rows.map { it.sharePercent })
        assertEquals(listOf(14, 39, null, -8, 77, -17, -27), rows.map { it.deltaPercent })
        assertEquals(1f, rows.first().barFraction, 0.0001f)
        assertEquals(208_000f / 480_000f, rows[1].barFraction, 0.0001f)
        assertEquals(1L, rows.first().key)
    }

    @Test fun rows_totalEqualsSummarySpent_andDropsNonPositive() {
        val rows = buildMapRows(parents + ParentSpend(8, "가짜분류", 0, 10_000))
        assertEquals(7, rows.size)
        assertEquals(1_194_000L, rows.sumOf { it.amountMinor })
        assertTrue(buildMapRows(emptyList()).isEmpty())
    }

    @Test fun tileTier_thresholds() {
        assertEquals(TileTier.BIG, tileTier(80f, 64f))
        assertEquals(TileTier.MID, tileTier(79.9f, 64f))
        assertEquals(TileTier.MID, tileTier(54f, 40f))
        assertEquals(TileTier.SMALL, tileTier(53.9f, 40f))
        assertEquals(TileTier.SMALL, tileTier(200f, 39.9f))
    }

    private fun row(day: Long, amount: Long, kind: String) =
        TransactionWithCategoryRow(day, day, amount, kind, 1, "식비", null, null, "", "MANUAL")

    @Test fun spentThroughDay_countsOnlyExpensesUpToElapsedDay() {
        val rows = listOf(row(100, 1_000, "EXPENSE"), row(102, 2_000, "EXPENSE"),
            row(103, 4_000, "EXPENSE"), row(101, 9_000, "INCOME"))
        assertEquals(3_000, spentThroughDay(rows, periodStartEpochDay = 100, elapsedDays = 2))
        assertEquals(0, spentThroughDay(emptyList(), 100, 2))
    }
}
