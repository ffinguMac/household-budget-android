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

/**
 * 거래 row의 아바타에 쓸 표시 텍스트를 결정한다.
 * 1순위: 소분류 아이콘, 2순위: 대분류 아이콘, 3순위: 소분류 이름 첫 글자.
 */
fun resolveCategoryDisplay(
    leafIcon: String?,
    parentIcon: String?,
    leafName: String,
): String =
    leafIcon?.takeIf { it.isNotBlank() }
        ?: parentIcon?.takeIf { it.isNotBlank() }
        ?: leafName.take(1)
