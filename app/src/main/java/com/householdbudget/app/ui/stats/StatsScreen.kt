package com.householdbudget.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.RecurringRuleEntity
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
import com.householdbudget.app.ui.theme.kindAccent
import com.householdbudget.app.ui.theme.kindSignPrefix
import com.householdbudget.app.ui.util.formatAmountGrouped
import com.householdbudget.app.ui.util.formatRangeShort
import com.householdbudget.app.ui.util.formatShortDayLabel
import com.householdbudget.app.ui.util.formatWon
import com.householdbudget.app.ui.util.paydayCountdownLabel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

// ── 모드 토글 (rememberSaveable 저장용) ────────────────────────────────────
private const val MODE_REPORT = "report"
private const val MODE_MAP = "map"

// ── 머니 맵 특수 타일 key ──────────────────────────────────────────────────
private const val SAVINGS_TILE_KEY = -1L
private const val OTHER_TILE_KEY = -2L

// ── 머니 맵 색 구간 (전월 대비 증감). 데이터 시각화 전용이라 하드코딩 허용. ──
private val MapDeepGreen = Color(0xFF1FA66F) // 델타 ≤ −20%
private val MapGreen = Color(0xFF2E6B52) // 델타 < 0%
private val MapNeutral = Color(0xFF3A4356) // ±5% 또는 전월 데이터 없음
private val MapRed = Color(0xFF8F3A44) // 델타 > 0%
private val MapDeepRed = Color(0xFFC2434E) // 델타 ≥ +20%
private val MapMint = Color(0xFF5EE8B5) // 남은 돈
private val MapOnMint = Color(0xFF06281A)
private val MapSavingsBlue = Color(0xFF2B5CA8) // 저축
private val MapOnSavings = Color(0xFFBDD7FF)
private val MapOnDelta = Color(0xFFEDF1F7)

private val statsZone = ZoneId.of("Asia/Seoul")

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
    val recurringRules by statsViewModel.recurringRules.collectAsStateWithLifecycle()
    val monthlyBudget by budgetViewModel.monthlyBudgetMinor.collectAsStateWithLifecycle()
    val categoryBudgets by budgetViewModel.categoryBudgets.collectAsStateWithLifecycle()

    val today = LocalDate.now(statsZone)
    var mode by rememberSaveable { mutableStateOf(MODE_REPORT) }
    var sheet by remember { mutableStateOf<SheetData?>(null) }

    // 집계는 (periodData) 가 바뀔 때만 다시 계산한다.
    val stats = remember(periodData) { periodData?.let(::computePeriodStats) }

    val otherLabel = stringResource(R.string.stats_map_other)
    val leftoverLabel = stringResource(R.string.stats_map_leftover)
    val overspentLabel = stringResource(R.string.stats_map_overspent)
    val savingsLabel = stringResource(R.string.stats_map_savings)
    val mapData =
        remember(stats, otherLabel, leftoverLabel, overspentLabel, savingsLabel) {
            stats?.let {
                buildMapTiles(it, otherLabel, leftoverLabel, overspentLabel, savingsLabel)
            }
        }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(bottom = FabContentBottomPadding),
    ) {
        item(key = "header") {
            StatsHeader(
                period = stats?.period,
                today = today,
                mode = mode,
                onModeChange = { mode = it },
            )
        }

        val currentStats = stats
        val currentData = periodData
        if (currentStats == null || currentData == null) {
            item(key = "skeleton") { StatsSkeleton() }
        } else if (mode == MODE_REPORT) {
            item(key = "gauge") {
                BudgetGaugeCard(
                    spentMinor = currentStats.expenseMinor,
                    budgetMinor = monthlyBudget,
                    daysRemaining =
                        ChronoUnit.DAYS
                            .between(today, currentStats.period.endExclusive)
                            .toInt()
                            .coerceAtLeast(1),
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
                CategoryBudgetCard(
                    parents = currentStats.parents.take(6),
                    budgets = categoryBudgets,
                    onRowClick = { parent ->
                        sheet =
                            SheetData(
                                title = parent.name,
                                rows = expenseRowsOfParent(currentData.current, parent.parentId),
                            )
                    },
                    modifier = cardItemModifier(),
                )
            }
            item(key = "upcoming") {
                UpcomingRulesCard(
                    rules = recurringRules,
                    today = today,
                    modifier = cardItemModifier(),
                )
            }
        } else {
            val tiles = mapData?.tiles.orEmpty()
            if (tiles.isEmpty()) {
                item(key = "map_empty") {
                    EmptyState(
                        icon = Icons.Outlined.PieChart,
                        title = stringResource(R.string.stats_map_empty_title),
                        description = stringResource(R.string.stats_map_empty_desc),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item(key = "map") {
                    Surface(
                        modifier = cardItemModifier(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        MoneyMap(
                            tiles = tiles,
                            onTileClick = { key ->
                                sheet =
                                    sheetForTileKey(
                                        key = key,
                                        stats = currentStats,
                                        rows = currentData.current,
                                        mergedParentIds = mapData?.mergedParentIds.orEmpty(),
                                        savingsLabel = savingsLabel,
                                        otherLabel = otherLabel,
                                    )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                                    .padding(Space.md),
                        )
                    }
                }
                item(key = "legend") { MapLegendRow(modifier = cardItemModifier()) }
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

private data class ParentSpend(
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

// ──────────────────────────────────────────────────────────────────────────
// 머니 맵 데이터
// ──────────────────────────────────────────────────────────────────────────

private data class MapData(
    val tiles: List<MapTile>,
    /** "기타" 타일로 합쳐진 대분류 id — 바텀시트 필터용. */
    val mergedParentIds: Set<Long>,
)

private fun deltaColor(currentMinor: Long, previousMinor: Long): Color {
    if (previousMinor <= 0L) return MapNeutral
    val delta = (currentMinor - previousMinor).toDouble() / previousMinor
    return when {
        abs(delta) <= 0.05 -> MapNeutral
        delta <= -0.20 -> MapDeepGreen
        delta < 0.0 -> MapGreen
        delta >= 0.20 -> MapDeepRed
        else -> MapRed
    }
}

private fun buildMapTiles(
    stats: PeriodStats,
    otherLabel: String,
    leftoverLabel: String,
    overspentLabel: String,
    savingsLabel: String,
): MapData {
    val expenseTiles =
        stats.parents
            .filter { it.currentMinor > 0 }
            .map { parent ->
                MapTile(
                    label = parent.name,
                    amountMinor = parent.currentMinor,
                    color = deltaColor(parent.currentMinor, parent.previousMinor),
                    onColor = MapOnDelta,
                    key = parent.parentId,
                )
            }
    val leftoverMinor = stats.incomeMinor - stats.expenseMinor - stats.savingsMinor
    val specialTiles = buildList {
        if (stats.savingsMinor > 0) {
            add(
                MapTile(
                    label = savingsLabel,
                    amountMinor = stats.savingsMinor,
                    color = MapSavingsBlue,
                    onColor = MapOnSavings,
                    key = SAVINGS_TILE_KEY,
                ),
            )
        }
        if (leftoverMinor > 0) {
            add(
                MapTile(
                    label = leftoverLabel,
                    amountMinor = leftoverMinor,
                    color = MapMint,
                    onColor = MapOnMint,
                    key = null,
                ),
            )
        } else if (leftoverMinor < 0) {
            add(
                MapTile(
                    label = overspentLabel,
                    amountMinor = -leftoverMinor,
                    color = MapDeepRed,
                    onColor = MapOnDelta,
                    key = null,
                ),
            )
        }
    }
    val totalShown =
        expenseTiles.sumOf { it.amountMinor } + specialTiles.sumOf { it.amountMinor }
    val merged =
        mergeSmallTiles(
            tiles = expenseTiles,
            otherLabel = otherLabel,
            otherColor = MapNeutral,
            otherOnColor = MapOnDelta,
            otherKey = OTHER_TILE_KEY,
            totalMinor = totalShown,
        )
    return MapData(
        tiles = merged.tiles + specialTiles,
        mergedParentIds = merged.mergedKeys.filterNotNull().toSet(),
    )
}

private fun sheetForTileKey(
    key: Long?,
    stats: PeriodStats,
    rows: List<TransactionWithCategoryRow>,
    mergedParentIds: Set<Long>,
    savingsLabel: String,
    otherLabel: String,
): SheetData? =
    when {
        key == null -> null
        key == SAVINGS_TILE_KEY ->
            SheetData(
                title = savingsLabel,
                rows =
                    rows
                        .filter { it.kind == CategoryKind.SAVINGS.storage }
                        .sortedByDescending { it.occurredEpochDay },
            )
        key == OTHER_TILE_KEY ->
            SheetData(
                title = otherLabel,
                rows =
                    rows
                        .filter {
                            it.kind == CategoryKind.EXPENSE.storage &&
                                parentKeyOf(it) in mergedParentIds
                        }
                        .sortedByDescending { it.occurredEpochDay },
            )
        else ->
            SheetData(
                title = stats.parents.firstOrNull { it.parentId == key }?.name ?: "",
                rows = expenseRowsOfParent(rows, key),
            )
    }

// ──────────────────────────────────────────────────────────────────────────
// 헤더 + 모드 토글
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsHeader(
    period: BudgetPeriod?,
    today: LocalDate,
    mode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = ScreenHorizontalPadding,
                    end = ScreenHorizontalPadding,
                    top = Space.xl,
                    bottom = Space.lg,
                ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text =
                    if (period != null) {
                        stringResource(R.string.stats_title, period.startInclusive.monthValue)
                    } else {
                        stringResource(R.string.stats_title_plain)
                    },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (period != null) {
                Text(
                    text = period.paydayCountdownLabel(today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = period.formatRangeShort(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ModeToggle(mode = mode, onModeChange = onModeChange)
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Row(modifier = Modifier.padding(Space.xs)) {
            ModeChip(
                label = stringResource(R.string.stats_mode_report),
                selected = mode == MODE_REPORT,
                onClick = { onModeChange(MODE_REPORT) },
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
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                )
                .clickable(onClick = onClick)
                .defaultMinSize(minWidth = 56.dp, minHeight = 40.dp)
                .padding(horizontal = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// 리포트: 예산 게이지
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun BudgetGaugeCard(
    spentMinor: Long,
    budgetMinor: Long?,
    daysRemaining: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        if (budgetMinor == null || budgetMinor <= 0L) {
            Column(
                modifier = Modifier.padding(Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = stringResource(R.string.stats_no_budget_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.stats_no_budget_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val ratio = spentMinor.toDouble() / budgetMinor
            val remainingMinor = budgetMinor - spentMinor
            val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val usedColor = MaterialTheme.colorScheme.primary
            val overColor = MaterialTheme.colorScheme.error
            Row(
                modifier = Modifier.padding(Space.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xl),
            ) {
                Box(
                    modifier = Modifier.size(128.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        val inset = 8.dp.toPx()
                        val arcSize =
                            Size(size.width - inset * 2, size.height - inset * 2)
                        val topLeft = Offset(inset, inset)
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                        val usedSweep = (ratio.coerceIn(0.0, 1.0) * 360.0).toFloat()
                        if (usedSweep > 0f) {
                            drawArc(
                                color = usedColor,
                                startAngle = -90f,
                                sweepAngle = usedSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = stroke,
                            )
                        }
                        if (ratio > 1.0) {
                            val overSweep =
                                ((ratio - 1.0).coerceAtMost(1.0) * 360.0).toFloat()
                            drawArc(
                                color = overColor,
                                startAngle = -90f,
                                sweepAngle = overSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = stroke,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text =
                                stringResource(
                                    R.string.stats_gauge_pct,
                                    (ratio * 100).roundToInt(),
                                ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (ratio > 1.0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                        Text(
                            text = stringResource(R.string.stats_gauge_center_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    if (remainingMinor >= 0) {
                        Text(
                            text =
                                stringResource(
                                    R.string.stats_gauge_remaining,
                                    remainingMinor.formatWon(),
                                ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.stats_gauge_daily,
                                    (remainingMinor / daysRemaining).formatWon(),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text =
                                stringResource(
                                    R.string.stats_gauge_over,
                                    (-remainingMinor).formatWon(),
                                ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.stats_gauge_over_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// 리포트: 주별 지출 바 차트
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun WeeklySpendCard(
    weeks: List<WeekSpend>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
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
                fontWeight = FontWeight.Bold,
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
                            isFuture ->
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            else ->
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.55f)
                                    .height((6 + 86 * fraction).dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barColor),
                        )
                        Text(
                            text = stringResource(R.string.stats_week_label, week.index + 1),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
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
// 리포트: 카테고리별 예산 대비
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryBudgetCard(
    parents: List<ParentSpend>,
    budgets: Map<Long, Long>,
    onRowClick: (ParentSpend) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(
                    text = stringResource(R.string.stats_category_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.stats_category_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (parents.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_category_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            parents.forEach { parent ->
                CategoryBudgetRow(
                    parent = parent,
                    budgetMinor = budgets[parent.parentId],
                    onClick = { onRowClick(parent) },
                )
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
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = parent.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    if (budgetMinor != null && budgetMinor > 0) {
                        parent.currentMinor.formatAmountGrouped() +
                            " / " +
                            budgetMinor.formatAmountGrouped()
                    } else {
                        parent.currentMinor.formatWon()
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            DeltaLabel(currentMinor = parent.currentMinor, previousMinor = parent.previousMinor)
        }
        if (budgetMinor != null && budgetMinor > 0) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (parent.currentMinor <= budgetMinor) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(
                                    (parent.currentMinor.toFloat() / budgetMinor)
                                        .coerceIn(0f, 1f),
                                )
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                    )
                } else {
                    // 초과: 바 전체 = 총지출, 뒤쪽 빨간 구간 = 예산 초과분.
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(budgetMinor.toFloat())
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .weight((parent.currentMinor - budgetMinor).toFloat())
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.error),
                        )
                    }
                }
            }
        }
    }
}

/** 전월 대비 델타: ▲12% 레드 / ▼3% 민트 / — (전월 0원 또는 동일). */
@Composable
private fun DeltaLabel(
    currentMinor: Long,
    previousMinor: Long,
    modifier: Modifier = Modifier,
) {
    val (text, color) =
        if (previousMinor <= 0L) {
            stringResource(R.string.stats_delta_flat) to
                MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            val pct =
                ((currentMinor - previousMinor) * 100.0 / previousMinor).roundToInt()
            when {
                pct > 0 ->
                    stringResource(R.string.stats_delta_up, pct) to
                        MaterialTheme.colorScheme.error
                pct < 0 ->
                    stringResource(R.string.stats_delta_down, -pct) to
                        MaterialTheme.colorScheme.primary
                else ->
                    stringResource(R.string.stats_delta_flat) to
                        MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier,
    )
}

// ──────────────────────────────────────────────────────────────────────────
// 리포트: 다가오는 자동이체
// ──────────────────────────────────────────────────────────────────────────

private fun nextOccurrence(rule: RecurringRuleEntity, today: LocalDate): LocalDate {
    val ym = YearMonth.from(today)
    val thisMonth = ym.atDay(minOf(rule.dayOfMonth, ym.lengthOfMonth()))
    if (!thisMonth.isBefore(today)) return thisMonth
    val next = ym.plusMonths(1)
    return next.atDay(minOf(rule.dayOfMonth, next.lengthOfMonth()))
}

@Composable
private fun UpcomingRulesCard(
    rules: List<RecurringRuleEntity>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val upcoming =
        remember(rules, today) {
            rules
                .filter { it.enabled }
                .map { rule -> rule to nextOccurrence(rule, today) }
                .sortedBy { (_, date) -> date }
                .take(2)
        }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = stringResource(R.string.stats_upcoming_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (upcoming.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_upcoming_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            upcoming.forEach { (rule, date) ->
                val kind = CategoryKind.fromStorage(rule.kind)
                val daysUntil = ChronoUnit.DAYS.between(today, date).toInt()
                val relative =
                    when (daysUntil) {
                        0 -> stringResource(R.string.stats_upcoming_today)
                        1 -> stringResource(R.string.stats_upcoming_tomorrow)
                        else -> stringResource(R.string.stats_upcoming_in_days, daysUntil)
                    }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Space.xxs),
                    ) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = date.formatShortDayLabel() + " · " + relative,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = kindSignPrefix(kind) + rule.amountMinor.formatWon(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = kindAccent(kind),
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// 맵 범례 + 상세 바텀시트 + 스켈레톤
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun MapLegendRow(modifier: Modifier = Modifier) {
    val entries =
        listOf(
            MapGreen to stringResource(R.string.stats_legend_less),
            MapNeutral to stringResource(R.string.stats_legend_same),
            MapRed to stringResource(R.string.stats_legend_more),
            MapMint to stringResource(R.string.stats_map_leftover),
            MapSavingsBlue to stringResource(R.string.stats_map_savings),
        )
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        entries.forEach { (color, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class SheetData(
    val title: String,
    val rows: List<TransactionWithCategoryRow>,
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
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        stringResource(
                            R.string.stats_sheet_total,
                            data.rows.size,
                            data.rows.sumOf { it.amountMinor }.formatWon(),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                items(data.rows, key = { it.id }) { row ->
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
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}
