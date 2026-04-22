package com.householdbudget.app.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.householdbudget.app.domain.BudgetPeriod
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val wonFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

private val periodFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd").withLocale(Locale.KOREA)

fun Long.formatWon(): String = "${wonFormatter.format(this)}원"

/** UI에 표시할 때는 [BudgetPeriod.endExclusive] 전날까지가 실제 포함 마지막 날이다. */
fun BudgetPeriod.formatRangeKorean(): String =
    "${startInclusive.format(periodFormatter)} ~ ${endExclusive.minusDays(1).format(periodFormatter)}"

/** 금액 입력 필드에 천 단위 콤마를 표시하는 VisualTransformation */
class ThousandSeparatorTransformation : VisualTransformation {
    private val fmt = NumberFormat.getNumberInstance(Locale.KOREA)

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = if (digits.isEmpty()) "" else fmt.format(digits.toLongOrNull() ?: 0L)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (formatted.isEmpty()) return 0
                var digitsSeen = 0
                for (i in formatted.indices) {
                    if (digitsSeen == offset) return i
                    if (formatted[i].isDigit()) digitsSeen++
                }
                return formatted.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                var digitCount = 0
                for (i in 0 until minOf(offset, formatted.length)) {
                    if (formatted[i].isDigit()) digitCount++
                }
                return digitCount.coerceAtMost(digits.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
