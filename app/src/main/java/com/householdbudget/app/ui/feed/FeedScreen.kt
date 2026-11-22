package com.householdbudget.app.ui.feed

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.HomeSummary
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.archive.archiveMonthTitle
import com.householdbudget.app.ui.components.BudgetProgressBar
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.FabContentBottomPadding
import com.householdbudget.app.ui.components.KindSummaryRow
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.components.cardBorder
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatRangeShort
import com.householdbudget.app.ui.util.formatShortDayLabel
import com.householdbudget.app.ui.util.formatWon
import com.householdbudget.app.ui.util.paydayCountdownLabel
import java.time.LocalDate
import kotlin.math.abs

/** 종류 필터에서 "전체"를 뜻하는 sentinel 값 (rememberSaveable 저장용). */
private const val FILTER_ALL = "ALL"

/** 기간을 옮길 때 새 내용이 들어오는 시간(ms). */
private const val PERIOD_ENTER_MS = 180

/** 기간 하나가 들어오는 애니메이션. direction: -1 왼쪽에서, +1 오른쪽에서, 0 없음. */
private class PeriodEnter(val direction: Int) {
    val progress = Animatable(if (direction == 0) 1f else 0f)
}

/**
 * 홈 + 내역을 합친 "가계부" 피드.
 *
 * 상단 바(‹ 11월 › · 검색) + 남은 예산 히어로 + 수입·지출·저축 + 확인 필요 띠 + 필터 + 날짜별 카드.
 * 월 이동은 [BudgetViewModel.ledgerPeriodOffset]을 그대로 재사용한다.
 */
@Composable
fun FeedScreen(
    budgetViewModel: BudgetViewModel,
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    pendingCount: Int = 0,
    onOpenPending: () -> Unit = {},
    onOpenBudget: () -> Unit = {},
) {
    val summary by budgetViewModel.ledgerSummary.collectAsStateWithLifecycle()
    val periodOffset by budgetViewModel.ledgerPeriodOffset.collectAsStateWithLifecycle()
    val monthlyBudgetMinor by budgetViewModel.monthlyBudgetMinor.collectAsStateWithLifecycle()
    val today by budgetViewModel.today.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    // 아이콘을 눌러 열 때만 포커스를 준다(스크롤로 다시 보일 때는 주지 않는다).
    var focusSearch by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    var kindFilterStorage by rememberSaveable { mutableStateOf(FILTER_ALL) }

    val currentSummary = summary
    val rows = currentSummary?.transactions.orEmpty()
    val selectedKind: CategoryKind? =
        if (kindFilterStorage == FILTER_ALL) null else CategoryKind.fromStorage(kindFilterStorage)
    val trimmedQuery = query.trim()
    val filterActive = trimmedQuery.isNotEmpty() || selectedKind != null
    val filteredRows =
        remember(rows, trimmedQuery, kindFilterStorage) {
            rows.filter { row ->
                (selectedKind == null || row.kind == selectedKind.storage) &&
                    (
                        trimmedQuery.isEmpty() ||
                            row.categoryName.contains(trimmedQuery, ignoreCase = true) ||
                            row.parentCategoryName
                                ?.contains(trimmedQuery, ignoreCase = true) == true ||
                            row.memo.contains(trimmedQuery, ignoreCase = true)
                    )
            }
        }
    val days = remember(filteredRows) { groupFeedDays(filteredRows) }
    val filteredSum = remember(filteredRows) { filteredRows.sumOf { it.amountMinor } }

    // 새 기간이 들어올 때만 페이드 + 슬라이드. 키가 바뀌면 새 Animatable 이라 연타해도 마지막 기간이 끝까지 들어온다.
    val periodStart = currentSummary?.period?.startInclusive?.toEpochDay()
    val previousStart = remember { arrayOfNulls<Long>(1) } // 재구성을 일으키지 않는 기억
    val enter =
        remember(periodStart) {
            PeriodEnter(slideDirection(previousStart[0], periodStart))
                .also { if (periodStart != null) previousStart[0] = periodStart }
        }
    LaunchedEffect(enter) { enter.progress.animateTo(1f, tween(PERIOD_ENTER_MS)) }
    val slidePx = with(LocalDensity.current) { 24.dp.toPx() }
    // 그리기 단계에서만 읽는다 → 프레임마다 재구성 없음.
    val periodEnter =
        remember(enter, slidePx) {
            Modifier.graphicsLayer {
                val p = enter.progress.value
                alpha = p
                translationX = (1f - p) * enter.direction * slidePx
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
        item(key = "header", contentType = "header") {
            FeedTopBar(
                period = currentSummary?.period,
                searchOpen = showSearch,
                periodOffset = periodOffset,
                today = today,
                periodEnter = periodEnter,
                onPrevious = budgetViewModel::previousPeriod,
                onNext = budgetViewModel::nextPeriod,
                onToggleSearch = {
                    showSearch = !showSearch
                    // 닫힌 검색어가 목록을 몰래 거르지 않게 한다.
                    if (!showSearch) query = ""
                    focusSearch = showSearch
                },
            )
        }

        item(key = "hero", contentType = "hero") {
            val heroModifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHorizontalPadding)
            if (currentSummary != null) {
                FeedHeroCard(
                    summary = currentSummary,
                    monthlyBudgetMinor = monthlyBudgetMinor,
                    today = today,
                    periodEnter = periodEnter,
                    onResetPeriod = budgetViewModel::resetPeriod,
                    onOpenBudget = onOpenBudget,
                    modifier = heroModifier,
                )
            } else {
                HeroSkeleton(modifier = heroModifier)
            }
        }

        item(key = "controls", contentType = "controls") {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding)
                        .padding(top = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                if (currentSummary != null) {
                    KindSummaryRow(
                        incomeMinor = currentSummary.totalIncomeMinor,
                        expenseMinor = currentSummary.totalExpenseMinor,
                        savingsMinor = currentSummary.totalSavingsMinor,
                        modifier = Modifier.then(periodEnter),
                    )
                }

                if (pendingCount > 0) {
                    PendingStrip(count = pendingCount, onClick = onOpenPending)
                }

                if (showSearch) {
                    LaunchedEffect(focusSearch) {
                        if (focusSearch) {
                            searchFocus.requestFocus()
                            focusSearch = false
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        placeholder = {
                            Text(text = stringResource(R.string.ledger_search_placeholder))
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon =
                            if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription =
                                                stringResource(R.string.ledger_search_clear),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    KindFilterChip(
                        selected = selectedKind == null,
                        onClick = { kindFilterStorage = FILTER_ALL },
                        label = stringResource(R.string.ledger_filter_all),
                    )
                    FILTER_ORDER.forEach { kind ->
                        KindFilterChip(
                            selected = selectedKind == kind,
                            onClick = { kindFilterStorage = kind.storage },
                            label = stringResource(kindFilterLabelRes(kind)),
                        )
                    }
                }
            }
        }

        when {
            currentSummary == null -> {
                // 첫 로딩 중: 위의 스켈레톤만 보여준다.
            }

            rows.isEmpty() -> {
                item(key = "empty", contentType = "empty") {
                    // EmptyState 는 좌우/상하 패딩을 스스로 처리한다.
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = stringResource(R.string.ledger_period_empty_title),
                        description = stringResource(R.string.ledger_period_empty_desc),
                        modifier = Modifier.fillMaxWidth().then(periodEnter),
                    )
                }
            }

            filteredRows.isEmpty() -> {
                item(key = "empty_filtered", contentType = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.ledger_filter_empty_title),
                        description = stringResource(R.string.ledger_filter_empty_desc),
                        actionLabel = stringResource(R.string.ledger_filter_reset),
                        onAction = {
                            query = ""
                            kindFilterStorage = FILTER_ALL
                        },
                        modifier = Modifier.fillMaxWidth().then(periodEnter),
                    )
                }
            }

            else -> {
                if (filterActive) {
                    item(key = "filter_summary", contentType = "filterSummary") {
                        val text =
                            if (selectedKind != null) {
                                stringResource(
                                    R.string.ledger_result_count_sum,
                                    filteredRows.size,
                                    filteredSum.formatWon(),
                                )
                            } else {
                                stringResource(R.string.ledger_result_count, filteredRows.size)
                            }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ScreenHorizontalPadding)
                                    .padding(top = Space.md)
                                    .then(periodEnter),
                        )
                    }
                }

                days.forEach { day ->
                    // 하루에 카드 하나: 그날의 행을 한 카드에 담는다.
                    item(key = "day_${day.epochDay}", contentType = "day") {
                        FeedDayCard(
                            day = day,
                            today = today,
                            onTransactionClick = onTransactionClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ScreenHorizontalPadding)
                                    .padding(top = Space.xl)
                                    .then(periodEnter),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 상단 바(56dp): ‹ 11월 › + 기간 "11.20 ~ 12.17", 오른쪽 검색 토글.
 * 같은 해 안에서는 화살표 위치가 고정된다(가장 넓은 "12월" 폭으로 라벨을 잡는다).
 */
@Composable
private fun FeedTopBar(
    period: BudgetPeriod?,
    periodOffset: Int,
    searchOpen: Boolean,
    today: LocalDate,
    periodEnter: Modifier,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.ledger_prev_period),
            )
        }
        if (period != null) {
            val start = period.startInclusive
            Box(modifier = periodEnter, contentAlignment = Alignment.Center) {
                Text(
                    text = archiveMonthTitle(start.withMonth(12), today),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.alpha(0f).clearAndSetSemantics { },
                )
                Text(
                    text = archiveMonthTitle(start, today),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        IconButton(
            onClick = onNext,
            enabled = periodOffset < 0,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.ledger_next_period),
            )
        }
        if (period != null) {
            Text(
                text = period.formatRangeShort(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = periodEnter,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleSearch, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(if (searchOpen) R.string.feed_close_search else R.string.feed_open_search),
            )
        }
    }
}

/**
 * 히어로: 남은 예산(또는 초과액·남은 돈) 하나를 크게.
 * 지난 기간이면 오른쪽 카운트다운 자리에 "오늘로" 칩. 하루 여유는 현재 기간이고 남았을 때만.
 */
@Composable
private fun FeedHeroCard(
    summary: HomeSummary,
    monthlyBudgetMinor: Long?,
    today: LocalDate,
    periodEnter: Modifier,
    onResetPeriod: () -> Unit,
    onOpenBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val period = summary.period
    // 오프셋은 탭 즉시 바뀌고 데이터는 조금 뒤에 오므로, 보이는 부분은 데이터의 기간으로 가른다.
    val isCurrent = period.contains(today)
    val daysRemaining = (period.endExclusive.toEpochDay() - today.toEpochDay()).coerceAtLeast(0L).toInt()
    val status = monthlyBudgetMinor?.let { budgetStatus(it, summary.totalExpenseMinor, daysRemaining) }
    val over = status != null && status.remainingMinor < 0
    // 지난 기간에는 "이번 달"이라고 하지 않는다.
    fun pick(current: Int, past: Int) = if (isCurrent) current else past

    val label: String
    val amount: String
    val suffix: String?
    when {
        status == null -> {
            label = stringResource(pick(R.string.feed_hero_no_budget_label, R.string.feed_hero_no_budget_label_past))
            // 0원에는 부호를 붙이지 않는다.
            amount = (if (summary.netMinor < 0) "−" else "") + abs(summary.netMinor).formatWon()
            suffix = null
        }
        status.remainingMinor < 0 -> {
            label = stringResource(pick(R.string.feed_hero_over_label, R.string.feed_hero_over_label_past))
            amount = (-status.remainingMinor).formatWon()
            suffix = stringResource(R.string.feed_hero_over_suffix)
        }
        else -> {
            label = stringResource(pick(R.string.feed_hero_left_label, R.string.feed_hero_left_label_past))
            amount = status.remainingMinor.formatWon()
            suffix = stringResource(R.string.feed_hero_left_suffix)
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = cardBorder(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(PERIOD_ENTER_MS))
                    .padding(Space.xl)
                    .then(periodEnter),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (isCurrent) {
                    Text(
                        text = period.paydayCountdownLabel(today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Surface(
                        onClick = onResetPeriod,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.ledger_back_to_today),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = amount,
                    style = MaterialTheme.typography.displaySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                )
                if (suffix != null) {
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.xs).alignByBaseline(),
                    )
                }
            }
            if (status != null && isCurrent && status.dailyAllowanceMinor > 0) {
                Text(
                    text = stringResource(R.string.feed_hero_daily, status.dailyAllowanceMinor.formatWon()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (monthlyBudgetMinor != null) {
                BudgetProgressBar(
                    spentMinor = summary.totalExpenseMinor,
                    budgetMinor = monthlyBudgetMinor,
                    modifier = Modifier.padding(top = Space.md),
                )
            } else {
                TextButton(onClick = onOpenBudget) {
                    Text(text = stringResource(R.string.feed_set_budget))
                }
            }
        }
    }
}

/** 히어로 카드 자리에 놓는 콜드 스타트 스켈레톤. */
@Composable
private fun HeroSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .height(150.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/** 확인 필요 띠(48dp). 띠 전체가 버튼. */
@Composable
private fun PendingStrip(
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = stringResource(R.string.feed_pending_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.feed_pending_action),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 날짜 머리줄("오늘 · 11.22 (일)" · "101,000원 씀") + 그날 행을 담은 카드 하나. */
@Composable
private fun FeedDayCard(
    day: FeedDay,
    today: LocalDate,
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = LocalDate.ofEpochDay(day.epochDay)
    val shortLabel = date.formatShortDayLabel()
    val dayLabel =
        when (date) {
            today -> stringResource(R.string.feed_day_relative, stringResource(R.string.feed_day_today), shortLabel)
            today.minusDays(1) -> stringResource(R.string.feed_day_relative, stringResource(R.string.feed_day_yesterday), shortLabel)
            else -> shortLabel
        }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (day.expenseMinor > 0) {
                Text(
                    text = stringResource(R.string.budget_bar_spent, day.expenseMinor.formatWon()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = cardBorder(),
        ) {
            Column {
                day.rows.forEachIndexed { index, row ->
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
                        onClick = { onTransactionClick(row.id) },
                    )
                }
            }
        }
    }
}

/** 필터 칩: 36dp 알약, 선택 primaryContainer, 미선택 1dp outline. heightIn 이라 M3 최소 48dp 터치가 살아 있다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        modifier = Modifier.heightIn(min = 36.dp),
        shape = CircleShape,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = Color.Transparent,
                borderWidth = 1.dp,
            ),
    )
}

/** 칩 순서: 전체 · 지출 · 수입 · 저축. */
private val FILTER_ORDER = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME, CategoryKind.SAVINGS)

private fun kindFilterLabelRes(kind: CategoryKind): Int =
    when (kind) {
        CategoryKind.INCOME -> R.string.ledger_filter_income
        CategoryKind.EXPENSE -> R.string.ledger_filter_expense
        CategoryKind.SAVINGS -> R.string.ledger_filter_savings
    }
