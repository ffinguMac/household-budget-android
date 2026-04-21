package com.householdbudget.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * 월급일(day-of-month) 설정으로 회계월 구간을 계산한다.
 *
 * 날짜 산술은 이 클래스에만 두고, UI나 DB 쿼리에서 직접 보정하지 않는다.
 */
class PeriodResolver {

    /**
     * 해당 연·월에서 설정된 월급일을 [LocalDate]로 반환한다.
     *
     * 일자 N이 해당 월에 없으면(예: 2월 31일) 그 달의 **마지막 날**로 클램프한다.
     *
     * @param yearMonth 대상 달
     * @param paydayDom 월급일(1–31)
     */
    fun paydayInMonth(yearMonth: YearMonth, paydayDom: Int): LocalDate {
        require(paydayDom in 1..31) { "paydayDom must be in 1..31" }
        val lastDay = yearMonth.lengthOfMonth()
        val day = minOf(paydayDom, lastDay)
        return yearMonth.atDay(day)
    }

    /**
     * [reference]가 속한 회계월(월급일 기준)을 반환한다.
     *
     * 규칙: 직전 월급일(포함)부터 다음 월급일(미포함)까지.
     * - [reference]가 이번 달 월급일 **당일 이후**(포함)이면 구간 시작은 **이번 달** 월급일.
     * - 그렇지 않으면 구간 시작은 **지난달** 월급일, 끝은 **이번 달** 월급일(미포함).
     */
    fun periodContaining(reference: LocalDate, paydayDom: Int): BudgetPeriod {
        val ym = YearMonth.from(reference)
        val thisMonthPayday = paydayInMonth(ym, paydayDom)
        return if (!reference.isBefore(thisMonthPayday)) {
            val nextYm = ym.plusMonths(1)
            BudgetPeriod(
                startInclusive = thisMonthPayday,
                endExclusive = paydayInMonth(nextYm, paydayDom),
            )
        } else {
            val prevYm = ym.minusMonths(1)
            BudgetPeriod(
                startInclusive = paydayInMonth(prevYm, paydayDom),
                endExclusive = thisMonthPayday,
            )
        }
    }

    /** 직전 회계월. */
    fun previousPeriod(period: BudgetPeriod, paydayDom: Int): BudgetPeriod {
        val endExclusive = period.startInclusive
        val startYm = YearMonth.from(endExclusive).minusMonths(1)
        val startInclusive = paydayInMonth(startYm, paydayDom)
        return BudgetPeriod(startInclusive = startInclusive, endExclusive = endExclusive)
    }

    /** 다음 회계월. */
    fun nextPeriod(period: BudgetPeriod, paydayDom: Int): BudgetPeriod {
        val startInclusive = period.endExclusive
        val endYm = YearMonth.from(startInclusive).plusMonths(1)
        val endExclusive = paydayInMonth(endYm, paydayDom)
        return BudgetPeriod(startInclusive = startInclusive, endExclusive = endExclusive)
    }
}
