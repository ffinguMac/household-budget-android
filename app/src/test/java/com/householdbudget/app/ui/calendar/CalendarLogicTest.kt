package com.householdbudget.app.ui.calendar

import com.householdbudget.app.domain.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalendarLogicTest {

    @Test
    fun grid_fiveWeeks_withNextPaydayMarker() { // 11.20(금) ~ 12.17(목), 다음 월급날 12.18(금)
        val g = calendarGridDays(BudgetPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 18)))
        assertEquals(35, g.size)
        assertEquals(listOf<LocalDate?>(null, null, null, null, null), g.take(5))
        assertEquals(LocalDate.of(2026, 11, 20), g[5])
        assertEquals(LocalDate.of(2026, 12, 17), g[32])
        assertEquals(LocalDate.of(2026, 12, 18), g[33])
        assertNull(g[34])
    }

    @Test
    fun grid_sixWeeks() { // 10.30(금) ~ 11.29(일), 다음 월급날 11.30(월)
        val g = calendarGridDays(BudgetPeriod(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 30)))
        assertEquals(42, g.size)
        assertEquals(LocalDate.of(2026, 11, 29), g[35])
        assertEquals(LocalDate.of(2026, 11, 30), g[36])
    }

    @Test
    fun grid_endsOnSaturday_hasNoMarker() { // 11.1(일) ~ 11.28(토)
        val g = calendarGridDays(BudgetPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 29)))
        assertEquals(28, g.size)
        assertEquals(LocalDate.of(2026, 11, 28), g.last())
    }
}
