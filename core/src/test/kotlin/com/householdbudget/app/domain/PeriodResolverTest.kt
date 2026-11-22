package com.householdbudget.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(LocalDate.of(2025, 5, 23), period.startInclusive)
        assertEquals(LocalDate.of(2025, 6, 25), period.endExclusive)
        assertTrue(period.contains(LocalDate.of(2025, 5, 26)))
    }

    @Test
    fun periodContaining_dayBeforePayday_startsPreviousMonthPayday() {
        val period = resolver.periodContaining(LocalDate.of(2025, 5, 22), paydayDom = 25)
        assertEquals(LocalDate.of(2025, 4, 25), period.startInclusive)
        assertEquals(LocalDate.of(2025, 5, 23), period.endExclusive)
        assertTrue(period.contains(LocalDate.of(2025, 5, 22)))
    }

    @Test
    fun periodContaining_onPaydayInclusive_startsThisMonthPayday() {
        val period = resolver.periodContaining(LocalDate.of(2025, 5, 23), paydayDom = 25)
        assertEquals(LocalDate.of(2025, 5, 23), period.startInclusive)
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

    private fun d(iso: String) = LocalDate.parse(iso)
    private fun period(start: String, end: String) = BudgetPeriod(d(start), d(end))

    @Test
    fun paydayInMonth_saturday_movesToFriday() {
        assertEquals(d("2026-11-20"), resolver.paydayInMonth(YearMonth.of(2026, 11), 21)) // 11-21 토
    }

    @Test
    fun paydayInMonth_sunday_movesToFriday() {
        assertEquals(d("2027-02-19"), resolver.paydayInMonth(YearMonth.of(2027, 2), 21)) // 02-21 일
    }

    @Test
    fun paydayInMonth_weekdayNonHoliday_unchanged() {
        assertEquals(d("2026-12-21"), resolver.paydayInMonth(YearMonth.of(2026, 12), 21)) // 월
    }

    @Test
    fun paydayInMonth_chuseok_movesToPrecedingBusinessDay() {
        // 2026-09-25(금) 추석, 09-24(목) 추석 연휴 → 09-23(수)
        assertEquals(d("2026-09-23"), resolver.paydayInMonth(YearMonth.of(2026, 9), 25))
    }

    @Test
    fun paydayInMonth_holidayRunPlusWeekend() {
        // 2025-10-09 한글날 ← 10-08 대체 ← 10-05~07 추석 ← 10-04 토 ← 10-03 개천절 → 10-02(목)
        assertEquals(d("2025-10-02"), resolver.paydayInMonth(YearMonth.of(2025, 10), 9))
        // 2025-01-30 설 연휴 ← 01-27~29 ← 01-25~26 주말 → 01-24(금)
        assertEquals(d("2025-01-24"), resolver.paydayInMonth(YearMonth.of(2025, 1), 30))
    }

    @Test
    fun paydayInMonth_day1_crossesIntoPreviousMonth() {
        assertEquals(d("2026-12-31"), resolver.paydayInMonth(YearMonth.of(2027, 1), 1)) // 신정(금) → 목
        assertEquals(d("2027-12-31"), resolver.paydayInMonth(YearMonth.of(2028, 1), 1)) // 신정(토) → 금
    }

    @Test
    fun paydayInMonth_day31_clampThenAdjust() {
        assertEquals(d("2026-02-27"), resolver.paydayInMonth(YearMonth.of(2026, 2), 31)) // 02-28 토
        assertEquals(d("2026-05-29"), resolver.paydayInMonth(YearMonth.of(2026, 5), 31)) // 05-31 일
    }

    @Test
    fun paydayInMonth_yearOutsideTable_weekendOnly() {
        assertEquals(d("2023-10-03"), resolver.paydayInMonth(YearMonth.of(2023, 10), 3)) // 개천절이지만 표 밖
        assertEquals(d("2031-02-28"), resolver.paydayInMonth(YearMonth.of(2031, 3), 1))  // 03-01 토
    }

    @Test
    fun periodContaining_betweenAdjustedAndNominal_belongsToNewPeriod() {
        val expected = period("2026-11-20", "2026-12-21")
        assertEquals(expected, resolver.periodContaining(d("2026-11-20"), 21))
        assertEquals(expected, resolver.periodContaining(d("2026-11-21"), 21))
        assertEquals(period("2026-10-21", "2026-11-20"), resolver.periodContaining(d("2026-11-19"), 21))
    }

    @Test
    fun periodContaining_payday1_lastDayOfDecember_belongsToJanuaryPeriod() {
        val january = period("2026-12-31", "2027-02-01")
        assertEquals(january, resolver.periodContaining(d("2026-12-31"), 1))
        assertEquals(january, resolver.periodContaining(d("2027-01-01"), 1))
        assertEquals(period("2026-12-01", "2026-12-31"), resolver.periodContaining(d("2026-12-30"), 1))
    }

    @Test
    fun periodContaining_payday31_februaryClampAdjusted() {
        assertEquals(period("2026-02-27", "2026-03-31"), resolver.periodContaining(d("2026-02-28"), 31))
    }

    @Test
    fun previousAndNext_acrossMonthBoundaryShift() {
        val january = resolver.periodContaining(d("2027-01-15"), 1)
        assertEquals(period("2026-12-01", "2026-12-31"), resolver.previousPeriod(january, 1))
        // 2027-03-01(월) 3·1절 → 앞으로 당겨 02-26(금). 뒤로 미루지 않는다.
        assertEquals(period("2027-02-01", "2027-02-26"), resolver.nextPeriod(january, 1))
    }

    @Test
    fun upgradeMidPeriod_previousStartsBeforeOldRuleStart() {
        // 옛 규칙의 시작일(명목 2026-11-21)이 저장된 상태에서 업데이트된 경우
        val current = resolver.periodContaining(d("2026-11-22"), 21)
        assertEquals(d("2026-11-20"), current.startInclusive)
        assertEquals(period("2026-10-21", "2026-11-20"), resolver.previousPeriod(current, 21))
    }

    @Test
    fun everyDay_inExactlyOnePeriod_over36Months_forEveryPayday() {
        val first = d("2025-01-01")
        val last = d("2027-12-31")
        for (dom in 1..31) {
            var prev: BudgetPeriod? = null
            var day = first
            while (!day.isAfter(last)) {
                val p = resolver.periodContaining(day, dom)
                val tag = "dom=$dom day=$day"
                assertTrue(tag, p.contains(day))
                assertTrue(tag, p.startInclusive.isBefore(p.endExclusive))
                assertTrue(tag, p.startInclusive.dayOfWeek.value <= 5)
                assertFalse(tag, KoreanHolidays.isHoliday(p.startInclusive))
                // 빈틈·겹침 없음: 하루 넘어가면 같은 구간이거나, 앞 구간의 끝 = 새 구간의 시작
                prev?.let { if (it != p) assertEquals(tag, it.endExclusive, p.startInclusive) }
                // 이동이 periodContaining과 일치
                assertEquals(tag, resolver.periodContaining(p.endExclusive, dom), resolver.nextPeriod(p, dom))
                assertEquals(tag, p, resolver.previousPeriod(resolver.nextPeriod(p, dom), dom))
                assertEquals(tag, p, resolver.nextPeriod(resolver.previousPeriod(p, dom), dom))
                prev = p
                day = day.plusDays(1)
            }
        }
    }

    // 월급일 20일, 2026-12-20은 일요일이라 12-18로 당겨진다
    @Test
    fun periodAtOffset_zeroPrevNext() {
        val ref = LocalDate.of(2026, 11, 22)
        assertEquals(BudgetPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 18)), resolver.periodAtOffset(ref, 20, 0))
        assertEquals(BudgetPeriod(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 20)), resolver.periodAtOffset(ref, 20, -1))
        assertEquals(BudgetPeriod(LocalDate.of(2026, 12, 18), LocalDate.of(2027, 1, 20)), resolver.periodAtOffset(ref, 20, 1))
    }

    @Test
    fun periodAtOffset_earlyPayday_pulledIntoPreviousMonth() { // 2027-01-01 신정 → 2026-12-31
        val p = resolver.periodAtOffset(LocalDate.of(2026, 12, 31), 1, 0)
        assertEquals(LocalDate.of(2026, 12, 31), p.startInclusive)
    }

    @Test
    fun nominalMonth_payday1PulledIntoDecember_isJanuary() { // 2027-01-01 신정 → 2026-12-31
        assertEquals(YearMonth.of(2027, 1), resolver.nominalMonth(LocalDate.of(2026, 12, 31), 1))
    }

    @Test
    fun nominalMonth_payday25_isStartMonth() {
        assertEquals(YearMonth.of(2025, 5), resolver.nominalMonth(LocalDate.of(2025, 5, 23), 25))
    }

    @Test
    fun nominalMonth_payday1NotShifted_isStartMonth() {
        assertEquals(YearMonth.of(2025, 4), resolver.nominalMonth(LocalDate.of(2025, 4, 1), 1))
    }

    @Test
    fun nominalMonth_consecutivePeriods_areConsecutiveMonths_everyPayday() {
        for (dom in 1..31) {
            var period = resolver.periodContaining(LocalDate.of(2025, 1, 15), dom)
            var prev = resolver.nominalMonth(period.startInclusive, dom)
            repeat(36) {
                period = resolver.nextPeriod(period, dom)
                val month = resolver.nominalMonth(period.startInclusive, dom)
                assertEquals("dom=$dom start=${period.startInclusive}", prev.plusMonths(1), month)
                prev = month
            }
        }
    }
}
