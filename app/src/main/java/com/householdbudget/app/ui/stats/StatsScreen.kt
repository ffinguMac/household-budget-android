package com.householdbudget.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.R
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.FabContentBottomPadding
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.categoryStyleOf
import com.householdbudget.app.ui.util.formatRangeShort
import com.householdbudget.app.ui.util.formatShortDayLabel
import com.householdbudget.app.ui.util.formatWon
import com.householdbudget.app.ui.util.paydayCountdownLabel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import com.householdbudget.app.ui.components.StatColumn
import com.householdbudget.app.ui.feed.budgetStatus
import com.householdbudget.app.ui.theme.kindDotColor
import kotlin.math.roundToInt

// ── 모드 토글 (rememberSaveable 저장용) ────────────────────────────────────
private const val MODE_LIST = "list"
private const val MODE_MAP = "map"

@Composable
fun StatsScreen(
    repository: BudgetRepository,
    budgetViewModel: BudgetViewModel,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val statsViewModel: StatsViewModel =
        viewModel(factory = remember(repository) { StatsViewModelFactory(repository) })

    val periodData by statsViewModel.periodData.collectAsStateWithLifecycle()
    val monthlyBudget by budgetViewModel.monthlyBudgetMinor.collectAsStateWithLifecycle()
    val categoryBudgets by budgetViewModel.categoryBudgets.collectAsStateWithLifecycle()

    val today by repository.today.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(MODE_LIST) }
    var sheet by remember { mutableStateOf<SheetData?>(null) }

    // 집계는 (periodData) 가 바뀔 때만 다시 계산한다.
    val stats = remember(periodData) { periodData?.let(::computePeriodStats) }

    val mapRows = remember(stats) { stats?.let { buildMapRows(it.parents) }.orEmpty() }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(bottom = FabContentBottomPadding),
    ) {
        item(key = "header") {
            StatsHeader(period = stats?.period, today = today)
        }

        val currentStats = stats
        val currentData = periodData
        if (currentStats == null || currentData == null) {
            item(key = "skeleton") { StatsSkeleton() }
        } else {
            item(key = "summary") {
                // 지난달 이맘때: 오늘이 기간 시작 후 며칠째인지만큼 지난 기간에서도 센다.
                val elapsed = ChronoUnit.DAYS.between(currentStats.period.startInclusive, today)
                val compare =
                    deltaPercent(
                        spentThroughDay(currentData.current, currentStats.period.startInclusive.toEpochDay(), elapsed),
                        spentThroughDay(
                            currentData.previous,
                            currentData.previousPeriod.startInclusive.toEpochDay(),
                            elapsed,
                        ),
                    )
                SummaryCard(
                    stats = currentStats,
                    budgetMinor = monthlyBudget,
                    comparePercent = compare,
                    modifier = cardItemModifier(),
                )
            }
            item(key = "weekly") {
                WeeklySpendCard(
                    weeks = currentStats.weeks,
                    today = today,
                    modifier = cardItemModifier(),
                )
            }
            item(key = "category") {
                val openParent: (Long) -> Unit = { key ->
                    sheet = sheetForParent(key = key, stats = currentStats, rows = currentData.current)
                }
                CategoryCard(
                    mode = mode,
                    onModeChange = { mode = it },
                    parents = currentStats.parents.take(6),
                    budgets = categoryBudgets,
                    mapRows = mapRows,
                    stats = currentStats,
                    onParentClick = openParent,
                    modifier = cardItemModifier(),
                )
            }
        }
    }

    val sheetData = sheet
    if (sheetData != null && sheetData.rows.isNotEmpty()) {
        TransactionsSheet(
            data = sheetData,
            onDismiss = { sheet = null },
        )
    }
}

private fun cardItemModifier(): Modifier =
    Modifier
        .fillMaxWidth()
        .padding(horizontal = ScreenHorizontalPadding)
        .padding(bottom = Space.md)

// ──────────────────────────────────────────────────────────────────────────
// 집계
// ──────────────────────────────────────────────────────────────────────────

private data class WeekSpend(
    val index: Int,
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
    val expenseMinor: Long,
)

internal data class ParentSpend(
    val parentId: Long,
    val name: String,
    val currentMinor: Long,
    val previousMinor: Long,
)

private data class PeriodStats(
    val period: BudgetPeriod,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val savingsMinor: Long,
    val weeks: List<WeekSpend>,
    /** EXPENSE 대분류별 지출, 이번 기간 금액 내림차순. */
    val parents: List<ParentSpend>,
)

private fun parentKeyOf(row: TransactionWithCategoryRow): Long =
    row.parentCategoryId ?: row.categoryId

private fun computePeriodStats(data: StatsPeriodData): PeriodStats {
    val current = data.current
    val incomeMinor =
        current.filter { it.kind == CategoryKind.INCOME.storage }.sumOf { it.amountMinor }
    val expenseRows = current.filter { it.kind == CategoryKind.EXPENSE.storage }
    val expenseMinor = expenseRows.sumOf { it.amountMinor }
    val savingsMinor =
        current.filter { it.kind == CategoryKind.SAVINGS.storage }.sumOf { it.amountMinor }

    // 주별: 기간 시작일부터 7일 단위로 자른다. 마지막 조각은 7일 미만일 수 있다.
    val weeks = ArrayList<WeekSpend>()
    var weekStart = data.period.startInclusive
    var index = 0
    while (weekStart.isBefore(data.period.endExclusive)) {
        val weekEnd = minOf(weekStart.plusDays(7), data.period.endExclusive)
        val startEpoch = weekStart.toEpochDay()
        val endEpoch = weekEnd.toEpochDay()
        weeks +=
            WeekSpend(
                index = index,
                startInclusive = weekStart,
                endExclusive = weekEnd,
                expenseMinor =
                    expenseRows
                        .filter { it.occurredEpochDay in startEpoch until endEpoch }
                        .sumOf { it.amountMinor },
            )
        weekStart = weekEnd
        index++
    }

    val previousByParent =
        data.previous
            .filter { it.kind == CategoryKind.EXPENSE.storage }
            .groupBy(::parentKeyOf)
            .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
    val parents =
        expenseRows
            .groupBy(::parentKeyOf)
            .map { (parentId, rows) ->
                ParentSpend(
                    parentId = parentId,
                    name = rows.first().parentCategoryName ?: rows.first().categoryName,
                    currentMinor = rows.sumOf { it.amountMinor },
                    previousMinor = previousByParent[parentId] ?: 0L,
                )
            }
            .sortedByDescending { it.currentMinor }

    return PeriodStats(
        period = data.period,
        incomeMinor = incomeMinor,
        expenseMinor = expenseMinor,
        savingsMinor = savingsMinor,
        weeks = weeks,
        parents = parents,
    )
}

private fun expenseRowsOfParent(
    rows: List<TransactionWithCategoryRow>,
    parentId: Long,
): List<TransactionWithCategoryRow> =
    rows
        .filter { it.kind == CategoryKind.EXPENSE.storage && parentKeyOf(it) == parentId }
        .sortedByDescending { it.occurredEpochDay }

private fun sheetForParent(
    key: Long,
    stats: PeriodStats,
    rows: List<TransactionWithCategoryRow>,
): SheetData =
    stats.parents.firstOrNull { it.parentId == key }.let { parent ->
        SheetData(
            title = parent?.name ?: "",
            rows = expenseRowsOfParent(rows, key),
            deltaPercent = parent?.let { deltaPercent(it.currentMinor, it.previousMinor) },
        )
    }

// ──────────────────────────────────────────────────────────────────────────
// 헤더
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsHeader(
    period: BudgetPeriod?,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = ScreenHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text =
                    if (period != null) {
                        stringResource(R.string.stats_title, period.startInclusive.monthValue)
                    } else {
                        stringResource(R.string.stats_title_plain)
                    },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (period != null) {
                Text(
                    text = period.formatRangeShort(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (period != null) {
            Text(
                text = period.paydayCountdownLabel(today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeToggle(
    mode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Row {
            ModeChip(
                label = stringResource(R.string.stats_mode_report),
                selected = mode == MODE_LIST,
                onClick = { onModeChange(MODE_LIST) },
            )
            ModeChip(
                label = stringResource(R.string.stats_mode_map),
                selected = mode == MODE_MAP,
                onClick = { onModeChange(MODE_MAP) },
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 눌리는 영역은 48dp, 보이는 알약은 32dp.
    Box(
        modifier = modifier.clickable(onClick = onClick).defaultMinSize(minWidth = 64.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .padding(horizontal = Space.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// 요약 카드
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    stats: PeriodStats,
    budgetMinor: Long?,
    comparePercent: Int?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    text = stringResource(R.string.stats_summary_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (comparePercent != null && comparePercent != 0) {
                    val less = comparePercent < 0
                    Text(
                        text =
                            stringResource(
                                if (less) R.string.stats_compare_less else R.string.stats_compare_more,
                                kotlin.math.abs(comparePercent),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (less) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Text(
                text = stats.expenseMinor.formatWon(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (budgetMinor != null && budgetMinor > 0L) {
                val status = budgetStatus(budgetMinor, stats.expenseMinor, daysRemaining = 1)
                val over = status.remainingMinor < 0
                Text(
                    text =
                        if (over) {
                            stringResource(R.string.stats_summary_budget_over, budgetMinor.formatWon(), (-status.remainingMinor).formatWon())
                        } else {
                            stringResource(
                                R.string.stats_summary_budget,
                                budgetMinor.formatWon(),
                                (status.usedFraction * 100).roundToInt(),
                                status.remainingMinor.formatWon(),
                            )
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThinBar(
                    fraction = status.usedFraction,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    height = 8.dp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                StatColumn(
                    label = stringResource(R.string.stats_col_spent),
                    value = stats.expenseMinor.formatWon(),
                    dotColor = kindDotColor(CategoryKind.EXPENSE),
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    label = stringResource(R.string.stats_col_savings),
                    value = stats.savingsMinor.formatWon(),
                    dotColor = kindDotColor(CategoryKind.SAVINGS),
                    modifier = Modifier.weight(1f),
                )
                StatColumn(
                    label = stringResource(R.string.stats_col_left),
                    value = (stats.incomeMinor - stats.expenseMinor - stats.savingsMinor).formatWon(),
                    dotColor = null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 둥근 진행 막대. [fraction]은 0..1. */
@Composable
private fun ThinBar(
    fraction: Float,
    color: Color,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(height).clip(CircleShape).background(trackColor),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

// ──────────────────────────────────────────────────────────────────────────
// 주별 지출 바 차트
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun WeeklySpendCard(
    weeks: List<WeekSpend>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Text(
                text = stringResource(R.string.stats_weekly_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val maxExpense = weeks.maxOfOrNull { it.expenseMinor }?.coerceAtLeast(1L) ?: 1L
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                weeks.forEach { week ->
                    val isCurrent =
                        !today.isBefore(week.startInclusive) &&
                            today.isBefore(week.endExclusive)
                    val isFuture = week.startInclusive.isAfter(today)
                    val barColor =
                        when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isFuture -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    val fraction = week.expenseMinor.toFloat() / maxExpense
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        if (week.expenseMinor > 0) {
                            Text(
                                text = compactAmountLabel(week.expenseMinor),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.55f)
                                    .height((6 + 86 * fraction).dp)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .background(barColor),
                        )
                        Text(
                            text = stringResource(R.string.stats_week_label, week.index + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// 카테고리 카드 (목록 / 맵)
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryCard(
    mode: String,
    onModeChange: (String) -> Unit,
    parents: List<ParentSpend>,
    budgets: Map<Long, Long>,
    mapRows: List<MapRow>,
    stats: PeriodStats,
    onParentClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stats_category_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ModeToggle(mode = mode, onModeChange = onModeChange)
            }
            if (mode == MODE_MAP) {
                if (mapRows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.PieChart,
                        title = stringResource(R.string.stats_map_empty_title),
                        description = stringResource(R.string.stats_map_empty_desc),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MapSummaryCard(
                        month = stats.period.startInclusive.monthValue,
                        summary = MapSummary(stats.incomeMinor, stats.expenseMinor, stats.savingsMinor),
                    )
                    MoneyMap(
                        rows = mapRows,
                        onTileClick = onParentClick,
                        modifier = Modifier.fillMaxWidth().height(230.dp),
                    )
                }
            } else {
                if (parents.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stats_category_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                parents.forEach { parent ->
                    CategoryBudgetRow(
                        parent = parent,
                        budgetMinor = budgets[parent.parentId],
                        onClick = { onParentClick(parent.parentId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetRow(
    parent: ParentSpend,
    budgetMinor: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = categoryStyleOf(parent.name)
    val hasBudget = budgetMinor != null && budgetMinor > 0L
    val over = hasBudget && parent.currentMinor > budgetMinor!!
    val barFraction =
        if (hasBudget) (parent.currentMinor.toFloat() / budgetMinor!!).coerceIn(0f, 1f) else 0f
    val caption =
        when {
            !hasBudget -> stringResource(R.string.stats_cat_no_budget)
            over ->
                stringResource(
                    R.string.stats_cat_over,
                    budgetMinor!!.formatWon(),
                    (parent.currentMinor - budgetMinor).formatWon(),
                )
            else ->
                stringResource(
                    R.string.stats_cat_pct,
                    budgetMinor!!.formatWon(),
                    (parent.currentMinor * 100.0 / budgetMinor).roundToInt(),
                )
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 64.dp)
                .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(style.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = style.emoji, style = MaterialTheme.typography.bodyLarge)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    text = parent.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = parent.currentMinor.formatWon(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (hasBudget) {
                ThinBar(
                    fraction = barFraction,
                    color = if (over) MaterialTheme.colorScheme.error else style.color,
                    height = 4.dp,
                )
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// 상세 바텀시트 + 스켈레톤
// ──────────────────────────────────────────────────────────────────────────

private data class SheetData(
    val title: String,
    val rows: List<TransactionWithCategoryRow>,
    val deltaPercent: Int? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionsSheet(
    data: SheetData,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val delta = data.deltaPercent
                Text(
                    text =
                        stringResource(
                            R.string.stats_sheet_total,
                            data.rows.size,
                            data.rows.sumOf { it.amountMinor }.formatWon(),
                        ) +
                            when {
                                delta == null -> ""
                                delta > 0 -> " · " + stringResource(R.string.stats_sheet_up, delta)
                                else -> " · " + stringResource(R.string.stats_sheet_down, -delta)
                            },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                items(data.rows, key = { it.id }) { row ->
                    // TransactionRow 자체 좌우 여백이 16dp라 4dp를 더해 제목과 20dp로 맞춘다.
                    Box(Modifier.padding(horizontal = Space.xs)) {
                        TransactionRow(
                            categoryName = row.categoryName,
                            parentCategoryName = row.parentCategoryName,
                            memo = row.memo,
                            amountMinor = row.amountMinor,
                            kind = CategoryKind.fromStorage(row.kind),
                            dateLabel = LocalDate.ofEpochDay(row.occurredEpochDay).formatShortDayLabel(),
                        )
                    }
                }
            }
        }
    }
}

/** 콜드 스타트 스켈레톤: 카드 두 장 자리. */
@Composable
private fun StatsSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}
