package com.householdbudget.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 월급일(day-of-month) 설정으로 회계월 구간을 계산한다.
 * 월급일이 주말·공휴일이면 직전 영업일로 당긴 날(보정일)이 구간 경계가 된다.
 *
 * 날짜 산술은 이 클래스에만 두고, UI나 DB 쿼리에서 직접 보정하지 않는다.
 */
class PeriodResolver {

    /**
     * 명목 월 [yearMonth]의 **보정된** 월급일을 반환한다.
     *
     * 일자 N이 해당 월에 없으면(예: 2월 31일) 그 달의 **마지막 날**로 클램프한 뒤,
     * 그 날이 토·일요일이거나 [KoreanHolidays] 공휴일이면 영업일이 나올 때까지 하루씩 앞으로 당긴다.
     * 그래서 앞 달 날짜일 수 있다(예: 2027-01, 1일 → 2026-12-31).
     *
     * @param yearMonth 명목 월
     * @param paydayDom 월급일(1–31)
     */
    fun paydayInMonth(yearMonth: YearMonth, paydayDom: Int): LocalDate {
        require(paydayDom in 1..31) { "paydayDom must be in 1..31" }
        var day = nominalPayday(yearMonth, paydayDom)
        while (!isBusinessDay(day)) day = day.minusDays(1)
        return day
    }

    /**
     * [reference]가 속한 회계월을 반환한다: 보정된 월급일(포함)부터 다음 명목 월의 보정된 월급일(미포함)까지.
     *
     * 명목 월 다음 달·이번 달·지난달 순서로 보며 보정일이 [reference] 이전(같은 날 포함)인 첫 달을 시작으로 삼는다.
     * 보정일이 앞 달로 넘어갈 수 있어 보정일에서 명목 월을 역산하지 않는다.
     */
    fun periodContaining(reference: LocalDate, paydayDom: Int): BudgetPeriod {
        val ym = YearMonth.from(reference)
        // 지난달 보정일은 항상 reference보다 앞이라 셋 중 하나는 반드시 걸린다.
        val start = listOf(ym.plusMonths(1), ym, ym.minusMonths(1))
            .first { !paydayInMonth(it, paydayDom).isAfter(reference) }
        return BudgetPeriod(
            startInclusive = paydayInMonth(start, paydayDom),
            endExclusive = paydayInMonth(start.plusMonths(1), paydayDom),
        )
    }

    /** 직전 회계월: [period] 시작 전날이 속한 구간. */
    fun previousPeriod(period: BudgetPeriod, paydayDom: Int): BudgetPeriod =
        periodContaining(period.startInclusive.minusDays(1), paydayDom)

    /** 다음 회계월: [period] 끝(미포함) 날이 속한 구간. */
    fun nextPeriod(period: BudgetPeriod, paydayDom: Int): BudgetPeriod =
        periodContaining(period.endExclusive, paydayDom)

    /** [reference]가 속한 구간에서 [offset]만큼 옮긴 구간. 0은 [periodContaining], 음수는 과거, 양수는 미래. */
    fun periodAtOffset(reference: LocalDate, paydayDom: Int, offset: Int): BudgetPeriod {
        var period = periodContaining(reference, paydayDom)
        repeat(-offset) { period = previousPeriod(period, paydayDom) }
        repeat(offset) { period = nextPeriod(period, paydayDom) }
        return period
    }

    /**
     * 시작일이 [periodStart]인 구간의 명목 월(제목에 쓰는 달).
     * 다음 달 월급일이 앞 달로 당겨져 시작일이 된 경우(예: 2027-01 1일 → 2026-12-31)는 다음 달이다.
     */
    fun nominalMonth(periodStart: LocalDate, paydayDom: Int): YearMonth {
        val next = YearMonth.from(periodStart).plusMonths(1)
        return if (paydayInMonth(next, paydayDom) == periodStart) next else YearMonth.from(periodStart)
    }

    private fun nominalPayday(yearMonth: YearMonth, paydayDom: Int): LocalDate =
        yearMonth.atDay(minOf(paydayDom, yearMonth.lengthOfMonth()))

    private fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY &&
            date.dayOfWeek != DayOfWeek.SUNDAY &&
            !KoreanHolidays.isHoliday(date)
}
