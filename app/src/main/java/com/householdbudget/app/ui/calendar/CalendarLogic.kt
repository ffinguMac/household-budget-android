package com.householdbudget.app.ui.calendar

import com.householdbudget.app.domain.BudgetPeriod
import java.time.LocalDate

/**
 * 월급 주기 달력 칸(일요일 시작, 7의 배수). 앞 빈칸 + 기간의 날들 + 마지막 줄에 자리가 남으면
 * 다음 월급날([BudgetPeriod.endExclusive]) 한 칸 + 뒤 빈칸. 빈칸은 null.
 */
fun calendarGridDays(period: BudgetPeriod): List<LocalDate?> {
    val cells = MutableList<LocalDate?>(period.startInclusive.dayOfWeek.value % 7) { null }
    var day = period.startInclusive
    while (day.isBefore(period.endExclusive)) {
        cells += day
        day = day.plusDays(1)
    }
    if (cells.size % 7 != 0) cells += period.endExclusive
    while (cells.size % 7 != 0) cells += null
    return cells
}
