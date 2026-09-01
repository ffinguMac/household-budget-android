package com.householdbudget.app.ui.ledger

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ReceiptLong
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.FabContentBottomPadding
import com.householdbudget.app.ui.components.KindSummaryRow
import com.householdbudget.app.ui.components.ScreenHeader
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.theme.kindAccent
import com.householdbudget.app.ui.theme.kindContainer
import com.householdbudget.app.ui.theme.kindOnContainer
import com.householdbudget.app.ui.theme.kindSignPrefix
import com.householdbudget.app.ui.util.formatDayLabel
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 종류 필터에서 "전체"를 뜻하는 sentinel 값 (rememberSaveable 저장용). */
private const val FILTER_ALL = "ALL"

private val periodMonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM").withLocale(Locale.KOREA)

private val periodDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM.dd").withLocale(Locale.KOREA)

@Composable
fun LedgerScreen(
    budgetViewModel: BudgetViewModel,
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val summary by budgetViewModel.ledgerSummary.collectAsStateWithLifecycle()
    val periodOffset by budgetViewModel.ledgerPeriodOffset.collectAsStateWithLifecycle()
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

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

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        state = listState,
        contentPadding = PaddingValues(bottom = FabContentBottomPadding),
    ) {
        item(key = "header") {
            // ScreenHeader 는 좌우/상하 패딩을 스스로 처리한다.
            ScreenHeader(
                title = stringResource(R.string.ledger_title),
                eyebrow =
                    stringResource(
                        if (periodOffset == 0) {
                            R.string.ledger_eyebrow_current
                        } else {
                            R.string.ledger_eyebrow_past
                        },
                    ),
            )
        }

        item(key = "header_controls") {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding)
                        .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (currentSummary != null) {
                    PeriodNavigator(
                        period = currentSummary.period,
                        nextEnabled = periodOffset < 0,
                        onPrevious = budgetViewModel::previousPeriod,
                        onNext = budgetViewModel::nextPeriod,
                    )
                    if (periodOffset != 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = budgetViewModel::resetPeriod) {
                                Text(text = stringResource(R.string.ledger_back_to_today))
                            }
                        }
                    }
                    // 회계월 전체 합계 (필터와 무관)
                    KindSummaryRow(
                        incomeMinor = currentSummary.totalIncomeMinor,
                        expenseMinor = currentSummary.totalExpenseMinor,
                        savingsMinor = currentSummary.totalSavingsMinor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SummarySkeletonRow()
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                // 첫 로딩 중: 헤더의 스켈레톤만 보여준다.
            }

            rows.isEmpty() -> {
                item(key = "empty") {
                    // EmptyState 는 좌우/상하 패딩을 스스로 처리한다.
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = stringResource(R.string.ledger_period_empty_title),
                        description = stringResource(R.string.ledger_period_empty_desc),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            filteredRows.isEmpty() -> {
                item(key = "empty_filtered") {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.ledger_filter_empty_title),
                        description = stringResource(R.string.ledger_filter_empty_desc),
                        actionLabel = stringResource(R.string.ledger_filter_reset),
                        onAction = {
                            query = ""
                            kindFilterStorage = FILTER_ALL
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                if (filterActive) {
                    item(key = "filter_summary") {
                        val text =
                            if (selectedKind != null) {
                                stringResource(
                                    R.string.ledger_result_count_sum,
                                    filteredRows.size,
                                    filteredRows.sumOf { it.amountMinor }.formatWon(),
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
                                    .padding(top = 12.dp),
                        )
                    }
                }

                val grouped =
                    filteredRows
                        .groupBy { it.occurredEpochDay }
                        .entries
                        .sortedByDescending { it.key }

                grouped.forEach { (epochDay, dayRows) ->
                    item(key = "header_$epochDay") {
                        val date = LocalDate.ofEpochDay(epochDay)
                        val isToday = date == today
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ScreenHorizontalPadding)
                                    .padding(top = 20.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            Spacer(Modifier.weight(1f))
                            CategoryKind.entries.forEach { kind ->
                                val dayTotal =
                                    dayRows
                                        .filter { it.kind == kind.storage }
                                        .sumOf { it.amountMinor }
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
                            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }

                    items(dayRows, key = { "tx_${it.id}" }) { row ->
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = ScreenHorizontalPadding,
                                        vertical = 3.dp,
                                    ),
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

/** `◀  2026.09 (09.25 ~ 10.24)  ▶` 형태의 회계월 이동 컨트롤. */
@Composable
private fun PeriodNavigator(
    period: BudgetPeriod,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.ledger_prev_period),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = period.startInclusive.format(periodMonthFormatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    period.startInclusive.format(periodDayFormatter) +
                        " ~ " +
                        period.endExclusive.minusDays(1).format(periodDayFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.ledger_next_period),
            )
        }
    }
}

/** 요약 타일 자리에 놓는 콜드 스타트 스켈레톤. */
@Composable
private fun SummarySkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
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
