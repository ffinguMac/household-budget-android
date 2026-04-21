package com.householdbudget.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodResolverTest {

    private val resolver = PeriodResolver()

    @Test
    fun paydayInMonth_clampsDay31InFebruary_nonLeap() {
        val feb2025 = YearMonth.of(2025, 2)
        assertEquals(LocalDate.of(2025, 2, 28), resolver.paydayInMonth(feb2025, 31))
    }

    @Test
    fun paydayInMonth_clampsDay31InFebruary_leap() {
        val feb2024 = YearMonth.of(2024, 2)
        assertEquals(LocalDate.of(2024, 2, 29), resolver.paydayInMonth(feb2024, 31))
    }

    @Test
    fun periodContaining_dayAfterPayday_startsThisMonthPayday() {
        val period = resolver.periodContaining(LocalDate.of(2025, 5, 26), paydayDom = 25)
        assertEquals(LocalDate.of(2025, 5, 25), period.startInclusive)
        assertEquals(LocalDate.of(2025, 6, 25), period.endExclusive)
        assertTrue(period.contains(LocalDate.of(2025, 5, 26)))
    }

    @Test
    fun periodContaining_dayBeforePayday_startsPreviousMonthPayday() {
        val period = resolver.periodContaining(LocalDate.of(2025, 5, 24), paydayDom = 25)
        assertEquals(LocalDate.of(2025, 4, 25), period.startInclusive)
        assertEquals(LocalDate.of(2025, 5, 25), period.endExclusive)
        assertTrue(period.contains(LocalDate.of(2025, 5, 24)))
    }

    @Test
    fun periodContaining_onPaydayInclusive_startsThisMonthPayday() {
        val period = resolver.periodContaining(LocalDate.of(2025, 5, 25), paydayDom = 25)
        assertEquals(LocalDate.of(2025, 5, 25), period.startInclusive)
        assertEquals(LocalDate.of(2025, 6, 25), period.endExclusive)
    }

    @Test
    fun periodContaining_payday31_januaryToFebruary() {
        val period = resolver.periodContaining(LocalDate.of(2025, 2, 1), paydayDom = 31)
        assertEquals(LocalDate.of(2025, 1, 31), period.startInclusive)
        assertEquals(LocalDate.of(2025, 2, 28), period.endExclusive)
    }

    @Test
    fun nextThenPrevious_roundTrip() {
        val original = resolver.periodContaining(LocalDate.of(2025, 6, 10), paydayDom = 25)
        val next = resolver.nextPeriod(original, paydayDom = 25)
        val back = resolver.previousPeriod(next, paydayDom = 25)
        assertEquals(original, back)
    }

    @Test
    fun previousThenNext_roundTrip() {
        val original = resolver.periodContaining(LocalDate.of(2025, 6, 10), paydayDom = 25)
        val prev = resolver.previousPeriod(original, paydayDom = 25)
        val back = resolver.nextPeriod(prev, paydayDom = 25)
        assertEquals(original, back)
    }
}
