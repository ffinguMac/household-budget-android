package com.householdbudget.app.ui.util

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
