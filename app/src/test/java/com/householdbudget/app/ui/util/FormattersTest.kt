package com.householdbudget.app.ui.util

import androidx.compose.ui.text.AnnotatedString
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

    @Test fun groupedDigits_cursorStaysAfterTypedDigit() {
        val t = GroupedDigitsTransformation.filter(AnnotatedString("300000"))
        assertEquals("300,000", t.text.text)
        // 끝에 커서: 6 → 7, 콤마 바로 뒤 숫자 앞(원래 3) → 화면 4
        assertEquals(7, t.offsetMapping.originalToTransformed(6))
        assertEquals(4, t.offsetMapping.originalToTransformed(3))
        assertEquals(3, t.offsetMapping.originalToTransformed(3 - 0).let { t.offsetMapping.transformedToOriginal(it) })
        assertEquals(6, t.offsetMapping.transformedToOriginal(7))
        val big = GroupedDigitsTransformation.filter(AnnotatedString("1234567"))
        assertEquals("1,234,567", big.text.text)
        for (o in 0..7) assertEquals(o, big.offsetMapping.transformedToOriginal(big.offsetMapping.originalToTransformed(o)))
        assertEquals(0, GroupedDigitsTransformation.filter(AnnotatedString("")).offsetMapping.originalToTransformed(0))
    }
}
