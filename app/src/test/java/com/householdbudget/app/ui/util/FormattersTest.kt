package com.householdbudget.app.ui.util

import com.householdbudget.app.domain.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormattersTest {
    @Test
    fun formatMonthDayWeekday_koreanNoPadding() {
        assertEquals("11월 20일(금)", LocalDate.of(2026, 11, 20).formatMonthDayWeekday())
        assertEquals("1월 2일(토)", LocalDate.of(2027, 1, 2).formatMonthDayWeekday())
        assertEquals("12월 31일(목)", LocalDate.of(2026, 12, 31).formatMonthDayWeekday())
    }

    @Test
    fun paydayCountdownLabel_haeyoWording() {
        val period = BudgetPeriod(startInclusive = LocalDate.of(2026, 11, 20), endExclusive = LocalDate.of(2026, 12, 18))
        assertEquals("월급까지 26일", period.paydayCountdownLabel(LocalDate.of(2026, 11, 22)))
        assertEquals("오늘이 월급날이에요", period.paydayCountdownLabel(LocalDate.of(2026, 11, 20)))
        assertEquals("내일이 월급날이에요", period.paydayCountdownLabel(LocalDate.of(2026, 12, 17)))
    }
}
