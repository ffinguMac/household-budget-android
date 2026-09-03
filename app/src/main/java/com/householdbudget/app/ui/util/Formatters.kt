package com.householdbudget.app.ui.util

import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.theme.kindSignPrefix
import java.math.BigInteger
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val wonFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

private val periodFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd").withLocale(Locale.KOREA)

private val dayLabelFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 (E)").withLocale(Locale.KOREA)

private val shortDayLabelFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM.dd (E)").withLocale(Locale.KOREA)

private val shortRangeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M.d").withLocale(Locale.KOREA)

fun Long.formatWon(): String = "${wonFormatter.format(this)}원"

/** UI에 표시할 때는 [BudgetPeriod.endExclusive] 전날까지가 실제 포함 마지막 날이다. */
fun BudgetPeriod.formatRangeKorean(): String =
    "${startInclusive.format(periodFormatter)} ~ ${endExclusive.minusDays(1).format(periodFormatter)}"

/** "원" 없이 천단위만. 예: 1234567 -> "1,234,567" */
fun Long.formatAmountGrouped(): String = wonFormatter.format(this)

/**
 * 입력 중인 숫자 문자열에 천단위 콤마. 숫자 이외 문자는 버린다. 앞자리 0 은 정리한다.
 * "" -> "", "0" -> "0", "007" -> "7", "1234567" -> "1,234,567"
 */
fun formatDigitsGrouped(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val trimmed = digits.trimStart('0')
    if (trimmed.isEmpty()) return "0"
    // Long 범위를 넘는 비정상 입력도 죽지 않게 BigInteger 로 그룹핑한다.
    return wonFormatter.format(BigInteger(trimmed))
}

/** [formatDigitsGrouped] 의 역: 콤마 등을 떼고 숫자만 남긴다. */
fun stripDigits(formatted: String): String = formatted.filter { it.isDigit() }

/** 부호 접두사 + 금액 + "원". 예: (150000, EXPENSE) -> "−150,000원" */
fun Long.formatSignedWon(kind: CategoryKind): String = kindSignPrefix(kind) + formatWon()

/**
 * 날짜 라벨. 오늘이면 "오늘", 어제면 "어제", 그 외 "M월 d일 (E)" (Locale.KOREA).
 * today 는 호출자가 주입 (테스트 가능성 + 자정 경계).
 */
fun LocalDate.formatDayLabel(today: LocalDate): String =
    when (this) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> format(dayLabelFormatter)
    }

/** 짧은 날짜 라벨: "MM.dd (E)" (Locale.KOREA) */
fun LocalDate.formatShortDayLabel(): String = format(shortDayLabelFormatter)

/**
 * "다음 월급까지 N일". 오늘이 기간 마지막 날이면 "내일이 월급날!",
 * 월급날 당일(=기간 시작일)이면 "오늘은 월급날 💸".
 * N 은 [BudgetPeriod.endExclusive](=다음 월급날)까지 남은 일수.
 */
fun BudgetPeriod.paydayCountdownLabel(today: LocalDate): String =
    when (today) {
        startInclusive -> "오늘은 월급날 💸"
        endExclusive.minusDays(1) -> "내일이 월급날!"
        else -> "다음 월급까지 ${ChronoUnit.DAYS.between(today, endExclusive)}일"
    }

/** "9.25 ~ 10.24" (연도 생략, 월.일). 끝은 실제 포함 마지막 날([BudgetPeriod.endExclusive] 전날). */
fun BudgetPeriod.formatRangeShort(): String =
    "${startInclusive.format(shortRangeFormatter)} ~ ${endExclusive.minusDays(1).format(shortRangeFormatter)}"
