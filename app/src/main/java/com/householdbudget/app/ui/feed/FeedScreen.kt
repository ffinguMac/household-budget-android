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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.HomeSummary
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.archive.archiveMonthTitle
import com.householdbudget.app.ui.components.BudgetProgressBar
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.FabContentBottomPadding
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.heroGradient
import com.householdbudget.app.ui.theme.kindAccent
import com.householdbudget.app.ui.theme.kindContainer
import com.householdbudget.app.ui.theme.kindOnContainer
import com.householdbudget.app.ui.theme.kindSignPrefix
import com.householdbudget.app.ui.theme.onHeroColor
import com.householdbudget.app.ui.theme.onHeroMutedColor
import com.householdbudget.app.ui.util.formatDayLabel
import com.householdbudget.app.ui.util.formatRangeShort
import com.householdbudget.app.ui.util.formatWon
import com.householdbudget.app.ui.util.paydayCountdownLabel
import java.time.LocalDate

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
 * 히어로(월급 카운트다운·남은 금액·예산 바) + 한 줄 요약 + 검색/필터 + 날짜 그룹 피드.
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
) {
    val summary by budgetViewModel.ledgerSummary.collectAsStateWithLifecycle()
    val periodOffset by budgetViewModel.ledgerPeriodOffset.collectAsStateWithLifecycle()
    val monthlyBudgetMinor by budgetViewModel.monthlyBudgetMinor.collectAsStateWithLifecycle()
    val today by budgetViewModel.today.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
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
            AppTopBar(title = stringResource(R.string.nav_feed))
        }

        if (pendingCount > 0) {
            item(key = "pendingBanner", contentType = "pendingBanner") {
                Text(
                    text = stringResource(R.string.auto_pending_banner, pendingCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding)
                            .padding(bottom = Space.sm)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = onOpenPending)
                            .padding(Space.md),
                )
            }
        }

        item(key = "hero", contentType = "hero") {
            if (currentSummary != null) {
                FeedHeroCard(
                    summary = currentSummary,
                    periodOffset = periodOffset,
                    monthlyBudgetMinor = monthlyBudgetMinor,
                    today = today,
                    periodEnter = periodEnter,
                    onPrevious = budgetViewModel::previousPeriod,
                    onNext = budgetViewModel::nextPeriod,
                    onResetPeriod = budgetViewModel::resetPeriod,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding),
                )
            } else {
                HeroSkeleton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding),
                )
            }
        }

        item(key = "controls", contentType = "controls") {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding)
                        .padding(top = Space.md, bottom = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                if (currentSummary != null) {
                    KindInlineSummary(
                        incomeMinor = currentSummary.totalIncomeMinor,
                        expenseMinor = currentSummary.totalExpenseMinor,
                        savingsMinor = currentSummary.totalSavingsMinor,
                        modifier = Modifier.fillMaxWidth().then(periodEnter),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
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
                    shape = MaterialTheme.shapes.large,
                )

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
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    CategoryKind.entries.forEach { kind ->
                        KindFilterChip(
                            selected = selectedKind == kind,
                            onClick = { kindFilterStorage = kind.storage },
                            label = stringResource(kindFilterLabelRes(kind)),
                            selectedContainerColor = kindContainer(kind),
                            selectedLabelColor = kindOnContainer(kind),
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
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
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
                    item(key = "header_${day.epochDay}", contentType = "dayHeader") {
                        val date = LocalDate.ofEpochDay(day.epochDay)
                        val isToday = date == today
                        // 회계월 안에서 월급날은 기간 시작일 하나뿐이다 (클램프 포함).
                        val isPayday = date == currentSummary.period.startInclusive
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ScreenHorizontalPadding)
                                    .padding(top = Space.xl, bottom = Space.sm)
                                    .then(periodEnter),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Text(
                                text = date.formatDayLabel(today),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (isToday) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                            if (isPayday) {
                                Text(
                                    text = stringResource(R.string.feed_payday_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = kindOnContainer(CategoryKind.INCOME),
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(kindContainer(CategoryKind.INCOME))
                                            .padding(horizontal = Space.sm, vertical = Space.xs),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            CategoryKind.entries.forEach { kind ->
                                val dayTotal = day.totalOf(kind)
                                if (dayTotal > 0) {
                                    Text(
                                        text = kindSignPrefix(kind) + dayTotal.formatWon(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = kindAccent(kind),
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding).then(periodEnter),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }

                    items(day.rows, key = { "tx_${it.id}" }, contentType = { "tx" }) { row ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = ScreenHorizontalPadding,
                                        vertical = 3.dp,
                                    )
                                    .then(periodEnter),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
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
    }
}

/**
 * 히어로 카드: 월 이동(‹ 9월 ›) + "다음 월급까지 N일" + 기간 + 남은 금액 + 예산 진행 바.
 * 지난 기간을 보는 중에는 카운트다운·예산 바 대신 "오늘로" 버튼을 보여준다.
 * 같은 해 안에서는 화살표 위치가 고정된다(연타해도 손가락 밑에서 움직이지 않는다).
 * 라벨에 연도가 붙는 순간(올해 → 지난해)에만 한 번 옮겨진다.
 */
@Composable
private fun FeedHeroCard(
    summary: HomeSummary,
    periodOffset: Int,
    monthlyBudgetMinor: Long?,
    today: LocalDate,
    periodEnter: Modifier,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onResetPeriod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val period = summary.period
    // 오프셋은 탭 즉시 바뀌고 데이터는 조금 뒤에 오므로, 보이는 부분은 데이터의 기간으로 가른다.
    val isCurrent = period.contains(today)
    // 0원에는 부호를 붙이지 않는다.
    val netPrefix =
        when {
            summary.netMinor > 0 -> "+"
            summary.netMinor < 0 -> "−"
            else -> ""
        }
    val absNet = if (summary.netMinor < 0) -summary.netMinor else summary.netMinor
    val start = period.startInclusive
    val monthLabel = archiveMonthTitle(start, today)
    // 같은 해 안에서 가장 넓은 "12월" 기준으로 라벨 폭을 고정한다(연도가 붙으면 폭이 한 번 바뀐다).
    val monthLabelFrame = archiveMonthTitle(start.withMonth(12), today)
    val daysRemaining =
        (period.endExclusive.toEpochDay() - today.toEpochDay())
            .coerceAtLeast(0L)
            .toInt()

    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.large)
                .background(heroGradient()),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(PERIOD_ENTER_MS))
                    .padding(horizontal = Space.xl, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            // ── 상단: 왼쪽 라벨/기간 · 오른쪽 컴팩트 월 이동 ‹ 8월 › ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).then(periodEnter),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier.heightIn(min = 24.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (isCurrent) {
                            Text(
                                text = period.paydayCountdownLabel(today),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = onHeroColor(),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.ledger_back_to_today),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = onHeroColor(),
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .background(onHeroColor().copy(alpha = 0.14f))
                                        .clickable(onClick = onResetPeriod)
                                        .padding(horizontal = Space.md, vertical = Space.xs),
                            )
                        }
                    }
                    Text(
                        text = period.formatRangeShort(),
                        style = MaterialTheme.typography.bodySmall,
                        color = onHeroMutedColor(),
                    )
                }
                // 48dp 터치 영역 안 20dp 글리프의 오른쪽 끝이 카드 안쪽 여백에 맞도록 14dp 민다.
                Row(
                    modifier = Modifier.offset(x = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.ledger_prev_period),
                            tint = onHeroColor(),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(modifier = periodEnter, contentAlignment = Alignment.Center) {
                        Text(
                            text = monthLabelFrame,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.alpha(0f).clearAndSetSemantics { },
                        )
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = onHeroColor(),
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = periodOffset < 0,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.ledger_next_period),
                            tint =
                                if (periodOffset < 0) {
                                    onHeroColor()
                                } else {
                                    onHeroColor().copy(alpha = 0.3f)
                                },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.home_net),
                style = MaterialTheme.typography.labelMedium,
                color = onHeroMutedColor(),
                modifier = periodEnter,
            )
            Text(
                text = netPrefix + absNet.formatWon(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = onHeroColor(),
                modifier = periodEnter,
            )

            if (isCurrent) {
                BudgetProgressBar(
                    spentMinor = summary.totalExpenseMinor,
                    budgetMinor = monthlyBudgetMinor,
                    daysRemaining = daysRemaining,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm)
                            .then(periodEnter),
                    onHero = true,
                )
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

/** 수입/지출/저축 한 줄 요약 (목업의 컴팩트 한 줄 텍스트). */
@Composable
private fun KindInlineSummary(
    incomeMinor: Long,
    expenseMinor: Long,
    savingsMinor: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindInlineItem(
            label = stringResource(R.string.ledger_filter_income),
            amountMinor = incomeMinor,
            kind = CategoryKind.INCOME,
        )
        KindInlineItem(
            label = stringResource(R.string.ledger_filter_expense),
            amountMinor = expenseMinor,
            kind = CategoryKind.EXPENSE,
        )
        KindInlineItem(
            label = stringResource(R.string.ledger_filter_savings),
            amountMinor = savingsMinor,
            kind = CategoryKind.SAVINGS,
        )
    }
}

@Composable
private fun KindInlineItem(
    label: String,
    amountMinor: Long,
    kind: CategoryKind,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            // 0원에는 부호를 붙이지 않는다 ("지출 −0원" 방지).
            text =
                if (amountMinor > 0) {
                    kindSignPrefix(kind) + amountMinor.formatWon()
                } else {
                    amountMinor.formatWon()
                },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = kindAccent(kind),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    selectedContainerColor: Color,
    selectedLabelColor: Color,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        modifier = modifier,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = selectedContainerColor,
                selectedLabelColor = selectedLabelColor,
            ),
    )
}

private fun kindFilterLabelRes(kind: CategoryKind): Int =
    when (kind) {
        CategoryKind.INCOME -> R.string.ledger_filter_income
        CategoryKind.EXPENSE -> R.string.ledger_filter_expense
        CategoryKind.SAVINGS -> R.string.ledger_filter_savings
    }
