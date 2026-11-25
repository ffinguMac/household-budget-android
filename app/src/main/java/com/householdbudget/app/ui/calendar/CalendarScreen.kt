package com.householdbudget.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.local.model.DayTotalRow
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.archive.archiveMonthTitle
import com.householdbudget.app.ui.components.FitText
import com.householdbudget.app.ui.components.FabContentBottomPadding
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.components.cardBorder
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatRangeShort
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayHeaderFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 (E)").withLocale(Locale.KOREA)

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    repository: BudgetRepository,
    paydayDom: Int,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val offset by viewModel.offset.collectAsStateWithLifecycle()
    val totals by viewModel.dayTotals.collectAsStateWithLifecycle()
    val today by repository.today.collectAsStateWithLifecycle()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = FabContentBottomPadding),
    ) {
        item {
            CalendarTopBar(
                period = period,
                offset = offset,
                today = today,
                paydayDom = paydayDom,
                onPrevious = viewModel::previousPeriod,
                onNext = viewModel::nextPeriod,
                onReset = viewModel::resetPeriod,
            )
        }
        val p = period ?: return@LazyColumn

        item {
            // ‹ › 직후 새 쿼리가 오기 전까지 totals에 이전 기간 행이 남아 있어 이 기간 것만 더한다.
            val rows = totals.values.filter { p.contains(LocalDate.ofEpochDay(it.dayEpoch)) }
            SummaryLine(
                expenseMinor = rows.sumOf { it.expenseMinor },
                incomeMinor = rows.sumOf { it.incomeMinor },
                savingsMinor = rows.sumOf { it.savingsMinor },
            )
        }

        item(key = "grid_${p.startInclusive}") {
            // 오늘이 이 기간에 있으면 오늘, 아니면 선택 없음.
            var selectedEpochDay by rememberSaveable(p.startInclusive) {
                mutableLongStateOf(if (p.contains(today)) today.toEpochDay() else Long.MIN_VALUE)
            }
            Column {
                CalendarGrid(
                    period = p,
                    today = today,
                    totals = totals,
                    selectedEpochDay = selectedEpochDay,
                    onSelect = { selectedEpochDay = it },
                )
                if (selectedEpochDay != Long.MIN_VALUE) {
                    DayDetailSection(
                        day = LocalDate.ofEpochDay(selectedEpochDay),
                        today = today,
                        repository = repository,
                    )
                }
            }
        }
    }
}

/** 상단(56dp): "11월" + "11.20 ~ 12.17 · 월급날 기준", 오른쪽 "오늘"(다른 기간일 때) ‹ ›. */
@Composable
private fun CalendarTopBar(
    period: BudgetPeriod?,
    offset: Int,
    today: LocalDate,
    paydayDom: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = ScreenHorizontalPadding, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 제목 칸만 줄어들게 해서 "오늘"과 ‹ ›는 360dp·큰 글꼴에서도 늘 48dp를 지킨다.
        Column(modifier = Modifier.weight(1f)) {
            if (period != null) {
                Text(
                    text = archiveMonthTitle(period.startInclusive, paydayDom, today),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.cal_period_basis, period.formatRangeShort()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (offset != 0) {
            TextButton(onClick = onReset) {
                Text(text = stringResource(R.string.cal_today), style = MaterialTheme.typography.labelLarge)
            }
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cal_prev_month),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cal_next_month),
            )
        }
    }
}

/** "지출 187,600원" + " · 수입 3,000,000원 · 저축 500,000원". */
@Composable
private fun SummaryLine(expenseMinor: Long, incomeMinor: Long, savingsMinor: Long) {
    val emphasis = MaterialTheme.typography.labelLarge.toSpanStyle()
        .merge(SpanStyle(color = MaterialTheme.colorScheme.onSurface))
    val expense = stringResource(R.string.cal_summary_expense, expenseMinor.formatWon())
    val rest = stringResource(R.string.cal_summary_rest, incomeMinor.formatWon(), savingsMinor.formatWon())
    // 금액이 커져도 두 줄로 넘어가지 않게 한 줄 안에서 글자를 줄인다.
    FitText(
        text = buildAnnotatedString {
            withStyle(emphasis) { append(expense) }
            append(rest)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding, vertical = Space.xs),
    )
}

@Composable
private fun CalendarGrid(
    period: BudgetPeriod,
    today: LocalDate,
    totals: Map<Long, DayTotalRow>,
    selectedEpochDay: Long,
    onSelect: (Long) -> Unit,
) {
    val weekLabels = stringArrayResource(R.array.cal_weekdays)
    val weeks = remember(period) { calendarGridDays(period).chunked(7) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg)
            .padding(top = Space.md),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = Space.xs)) {
            weekLabels.forEach { w ->
                Text(
                    text = w,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        weeks.forEach { week ->
            // 수입·지출 두 줄 칸이나 큰 글꼴이면 그 주 전체가 같이 커진다.
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                week.forEach { date ->
                    val cellModifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp)
                    if (date == null) {
                        Spacer(cellModifier)
                    } else {
                        val epoch = date.toEpochDay()
                        CalendarCell(
                            date = date,
                            period = period,
                            total = totals[epoch],
                            isToday = date == today,
                            isSelected = epoch == selectedEpochDay,
                            onClick = { onSelect(epoch) },
                            modifier = cellModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    date: LocalDate,
    period: BudgetPeriod,
    total: DayTotalRow?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 다음 월급날 칸은 다음 기간 몫이라 누를 수 없다.
    val isNextPayday = date == period.endExclusive
    val expense = total?.expenseMinor ?: 0L
    val income = total?.incomeMinor ?: 0L
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // 금액은 점으로만 보여 주고, 아무 내역이 없을 때만 월급·달 바뀜 글자를 쓴다.
    val label = when {
        income > 0 || expense > 0 -> null
        date == period.startInclusive || isNextPayday -> stringResource(R.string.cal_payday) to primary
        date.dayOfMonth == 1 -> stringResource(R.string.archive_month_label, date.monthValue) to muted
        else -> null
    }
    val description = listOfNotNull(
        stringResource(R.string.cal_cell_day, date.dayOfMonth),
        if (income > 0) stringResource(R.string.cal_cell_income, income.formatWon()) else null,
        if (expense > 0) stringResource(R.string.cal_cell_expense, expense.formatWon()) else null,
    ).joinToString(", ")
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .then(
                if (isNextPayday) Modifier else Modifier.selectable(selected = isSelected, onClick = onClick),
            )
            .padding(top = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 숫자 칸은 28dp, 오늘 링(32dp)은 칸 밖으로 그려 점·글자가 들어갈 자리를 남긴다.
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isToday) {
                        Modifier.drawBehind {
                            val stroke = 1.5.dp.toPx()
                            drawCircle(primary, radius = 16.dp.toPx() - stroke / 2, style = Stroke(stroke))
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isNextPayday) muted else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (income > 0 || expense > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                if (expense > 0) Box(Modifier.size(6.dp).background(muted, CircleShape))
                if (income > 0) Box(Modifier.size(6.dp).background(primary, CircleShape))
            }
        }
        label?.let { (text, color) ->
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
        }
    }
}

/** 선택 날짜 머리줄 "11월 22일 (일) · 오늘" + "4건 · 101,000원", 그 아래 하루 카드. */
@Composable
private fun DayDetailSection(
    day: LocalDate,
    today: LocalDate,
    repository: BudgetRepository,
) {
    val epoch = day.toEpochDay()
    val txs by remember(repository, epoch) { repository.observeTransactionsOnDay(epoch) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val dateText = day.format(dayHeaderFormatter)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (day == today) stringResource(R.string.cal_day_today, dateText) else dateText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // 금액은 지출만 더하므로 건수도 지출만 센다. 지출이 없으면 전체 건수만.
            val expenses = txs.filter { CategoryKind.fromStorage(it.kind) == CategoryKind.EXPENSE }
            if (txs.isNotEmpty()) {
                Text(
                    text = if (expenses.isEmpty()) {
                        stringResource(R.string.cal_tx_count, txs.size)
                    } else {
                        stringResource(R.string.cal_day_summary, expenses.size, expenses.sumOf { it.amountMinor }.formatWon())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (txs.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_day_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = cardBorder(),
            ) {
                Column {
                    txs.forEachIndexed { index, row ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        TransactionRow(
                            categoryName = row.categoryName,
                            parentCategoryName = row.parentCategoryName,
                            memo = row.memo,
                            amountMinor = row.amountMinor,
                            kind = CategoryKind.fromStorage(row.kind),
                        )
                    }
                }
            }
        }
    }
}
