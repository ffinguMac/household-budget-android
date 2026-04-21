package com.householdbudget.app.domain

import java.time.LocalDate

/**
 * 월급일 기준 회계월(예산 기간).
 *
 * 구간은 **[startInclusive, endExclusive)** 반열린 구간으로 표현한다.
 */
data class BudgetPeriod(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
) {
    init {
        require(startInclusive.isBefore(endExclusive)) {
            "startInclusive must be strictly before endExclusive"
        }
    }

    /** [startInclusive, endExclusive) 안에 [date]가 포함되는지 여부. */
    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startInclusive) && date.isBefore(endExclusive)
}
