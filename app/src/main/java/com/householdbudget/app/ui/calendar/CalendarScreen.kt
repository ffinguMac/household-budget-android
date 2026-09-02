package com.householdbudget.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.FabContentBottomPadding
import com.householdbudget.app.ui.components.KindSummaryRow
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatDayLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    repository: BudgetRepository,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val ym by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val totals by viewModel.dayTotals.collectAsStateWithLifecycle()
    val paydayDom by repository.paydayDom.collectAsStateWithLifecycle(initialValue = 25)
    var selectedEpochDay by remember { mutableLongStateOf(Long.MIN_VALUE) }

    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    val monthlyIncome = totals.values.sumOf { it.incomeMinor }
    val monthlyExpense = totals.values.sumOf { it.expenseMinor }
    val monthlySavings = totals.values.sumOf { it.savingsMinor }

    val first = ym.atDay(1)
    // Sunday-first: SUN=7 → 7%7=0, MON=1 → 1%7=1, ..., SAT=6 → 6%7=6
    val offset = first.dayOfWeek.value % 7
    val daysInMonth = ym.lengthOfMonth()
    // 월급 받는 날 (31일이 없는 달은 말일로 조정 — PeriodResolver 와 동일한 클램프 규칙)
    val paydayDay = minOf(paydayDom, daysInMonth)
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7
    val weekLabels = stringArrayResource(R.array.cal_weekdays)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = FabContentBottomPadding),
    ) {
        // 월 네비게이션
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = Space.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cal_prev_month),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = DateTimeFormatter.ofPattern("yyyy년 M월").withLocale(Locale.KOREA).format(ym),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cal_next_month),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // 월별 수입/지출/저축 3분할 요약
        item {
            KindSummaryRow(
                incomeMinor = monthlyIncome,
                expenseMinor = monthlyExpense,
                savingsMinor = monthlySavings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.lg))
        }

        // 달력 그리드
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
            ) {
                Column {
                    // 요일 헤더 (일=빨강, 토=파랑)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Space.md)) {
                        weekLabels.forEachIndexed { index, w ->
                            Text(
                                text = w,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (index) {
                                    0 -> MaterialTheme.colorScheme.error
                                    6 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                    // 날짜 행
                    for (row in 0 until totalCells / 7) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val i = row * 7 + col
                                val dayNum = i - offset + 1
                                val inMonth = dayNum in 1..daysInMonth
                                val isToday = inMonth && ym.atDay(dayNum) == today
                                val epoch = if (inMonth) ym.atDay(dayNum).toEpochDay() else -1L
                                val t = if (inMonth) totals[epoch] else null
                                val isSelected = inMonth && epoch == selectedEpochDay

                                CalendarCell(
                                    modifier = Modifier.weight(1f),
                                    dayNum = dayNum,
                                    inMonth = inMonth,
                                    isToday = isToday,
                                    isSelected = isSelected,
                                    isPayday = inMonth && dayNum == paydayDay,
                                    hasIncome = (t?.incomeMinor ?: 0L) > 0L,
                                    hasExpense = (t?.expenseMinor ?: 0L) > 0L,
                                    hasSavings = (t?.savingsMinor ?: 0L) > 0L,
                                    onClick = {
                                        if (inMonth) {
                                            val tapped = ym.atDay(dayNum).toEpochDay()
                                            selectedEpochDay = if (selectedEpochDay == tapped) Long.MIN_VALUE else tapped
                                        }
                                    },
                                )
                            }
                        }
                        if (row < totalCells / 7 - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }

        // 선택된 날짜 상세 (격자 바로 아래)
        if (selectedEpochDay != Long.MIN_VALUE) {
            val selectedDay = LocalDate.ofEpochDay(selectedEpochDay)
            item(key = "day_detail") {
                DayDetailSection(
                    day = selectedDay,
                    today = today,
                    repository = repository,
                )
            }
        }
    }
}

@Composable
private fun CalendarCell(
    dayNum: Int,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    isPayday: Boolean,
    hasIncome: Boolean,
    hasExpense: Boolean,
    hasSavings: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(0.85f)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .then(if (inMonth) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Space.sm),
    ) {
        // 오늘은 primary 채운 원으로 명확하게 표시
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (inMonth) dayNum.toString() else "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.primary
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> Color.Transparent
                },
                maxLines = 1,
            )
        }
        // 월급 받는 날 표시 — 우상단 민트 점
        if (isPayday) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (inMonth && (hasIncome || hasExpense || hasSavings)) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (hasIncome) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                }
                if (hasExpense) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
                if (hasSavings) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDetailSection(
    day: LocalDate,
    today: LocalDate,
    repository: BudgetRepository,
    modifier: Modifier = Modifier,
) {
    val epoch = day.toEpochDay()
    val txs by repository.observeTransactionsOnDay(epoch).collectAsStateWithLifecycle(initialValue = emptyList())
    val title = day.formatDayLabel(today)

    Column(modifier = modifier.fillMaxWidth()) {
        // 헤더
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(top = Space.xxl, bottom = Space.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.cal_tx_count, txs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.xs))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }

        // 거래 목록
        if (txs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.CalendarMonth,
                title = stringResource(R.string.calendar_empty_title),
                description = stringResource(R.string.calendar_day_empty),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                txs.forEach { row ->
                    val kind = CategoryKind.fromStorage(row.kind)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        TransactionRow(
                            categoryName = row.categoryName,
                            parentCategoryName = row.parentCategoryName,
                            memo = row.memo,
                            amountMinor = row.amountMinor,
                            kind = kind,
                            dateLabel = null,
                            onClick = null,
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
            }
        }
    }
}
