package com.householdbudget.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding

import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LedgerScreen(
    budgetViewModel: BudgetViewModel,
    ledgerViewModel: LedgerViewModel,
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by ledgerViewModel.results.collectAsStateWithLifecycle()
    val filter by ledgerViewModel.filter.collectAsStateWithLifecycle()
    val summary by budgetViewModel.homeSummary.collectAsStateWithLifecycle()
    val dateFmt = DateTimeFormatter.ofPattern("M월 d일 (E)").withLocale(Locale.KOREA)
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "이번 회계월",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                        1.2f,
                        androidx.compose.ui.unit.TextUnitType.Sp,
                    ),
                )
                Text(
                    text = "거래 내역",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                // 수입 / 지출 / 저축 요약 카드 3열
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LedgerSummaryTile(
                        modifier = Modifier.weight(1f),
                        title = "총 수입",
                        amount = "+${summary.totalIncomeMinor.formatWon()}",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    LedgerSummaryTile(
                        modifier = Modifier.weight(1f),
                        title = "총 지출",
                        amount = "−${summary.totalExpenseMinor.formatWon()}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    LedgerSummaryTile(
                        modifier = Modifier.weight(1f),
                        title = "총 저축",
                        amount = "↓${summary.totalSavingsMinor.formatWon()}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ── 검색 + 필터 ────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = ledgerViewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (filter.isActive) {
                            TextButton(onClick = ledgerViewModel::clear) {
                                Text(stringResource(R.string.filter_clear))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                )

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = filter.kind == null,
                            onClick = { ledgerViewModel.setKind(null) },
                            label = { Text(stringResource(R.string.filter_kind_all)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.kind == com.householdbudget.app.domain.CategoryKind.INCOME,
                            onClick = { ledgerViewModel.setKind(com.householdbudget.app.domain.CategoryKind.INCOME) },
                            label = { Text(stringResource(R.string.kind_income)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.kind == com.householdbudget.app.domain.CategoryKind.EXPENSE,
                            onClick = { ledgerViewModel.setKind(com.householdbudget.app.domain.CategoryKind.EXPENSE) },
                            label = { Text(stringResource(R.string.kind_expense)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.kind == com.householdbudget.app.domain.CategoryKind.SAVINGS,
                            onClick = { ledgerViewModel.setKind(com.householdbudget.app.domain.CategoryKind.SAVINGS) },
                            label = { Text(stringResource(R.string.kind_savings)) },
                        )
                    }
                }

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = filter.sortBy == LedgerSortBy.DATE_DESC,
                            onClick = { ledgerViewModel.setSort(LedgerSortBy.DATE_DESC) },
                            label = { Text(stringResource(R.string.filter_sort_date_desc)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.sortBy == LedgerSortBy.DATE_ASC,
                            onClick = { ledgerViewModel.setSort(LedgerSortBy.DATE_ASC) },
                            label = { Text(stringResource(R.string.filter_sort_date_asc)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.sortBy == LedgerSortBy.AMOUNT_DESC,
                            onClick = { ledgerViewModel.setSort(LedgerSortBy.AMOUNT_DESC) },
                            label = { Text(stringResource(R.string.filter_sort_amount_desc)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter.sortBy == LedgerSortBy.AMOUNT_ASC,
                            onClick = { ledgerViewModel.setSort(LedgerSortBy.AMOUNT_ASC) },
                            label = { Text(stringResource(R.string.filter_sort_amount_asc)) },
                        )
                    }
                }

                if (filter.isActive) {
                    Text(
                        text = stringResource(R.string.filter_result_count, rows.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (rows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding, vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.ledger_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            val grouped = rows.groupBy { it.occurredEpochDay }.entries.sortedByDescending { it.key }

            grouped.forEach { (epochDay, dayRows) ->
                val date = LocalDate.ofEpochDay(epochDay)
                val isToday = date == today
                val dateLabel = if (isToday) "오늘" else date.format(dateFmt)
                val dayIncome = dayRows.filter { it.kind == com.householdbudget.app.domain.CategoryKind.INCOME.storage }.sumOf { it.amountMinor }
                val dayExpense = dayRows.filter { it.kind == com.householdbudget.app.domain.CategoryKind.EXPENSE.storage }.sumOf { it.amountMinor }
                val daySavings = dayRows.filter { it.kind == com.householdbudget.app.domain.CategoryKind.SAVINGS.storage }.sumOf { it.amountMinor }

                item(key = "header_$epochDay") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding)
                            .padding(top = 20.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        if (dayIncome > 0) {
                            Text(
                                text = "+${dayIncome.formatWon()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (dayExpense > 0) {
                            Text(
                                text = "−${dayExpense.formatWon()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (daySavings > 0) {
                            Text(
                                text = "↓${daySavings.formatWon()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }

                items(dayRows, key = { "tx_${it.id}" }) { row ->
                    val kind = com.householdbudget.app.domain.CategoryKind.fromStorage(row.kind)
                    val amountColor = when (kind) {
                        com.householdbudget.app.domain.CategoryKind.INCOME -> MaterialTheme.colorScheme.secondary
                        com.householdbudget.app.domain.CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primary
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> MaterialTheme.colorScheme.error
                    }
                    val avatarBg = when (kind) {
                        com.householdbudget.app.domain.CategoryKind.INCOME -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        com.householdbudget.app.domain.CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val avatarTextColor = when (kind) {
                        com.householdbudget.app.domain.CategoryKind.INCOME -> MaterialTheme.colorScheme.onSecondaryContainer
                        com.householdbudget.app.domain.CategoryKind.SAVINGS -> MaterialTheme.colorScheme.onPrimaryContainer
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val amountPrefix = when (kind) {
                        com.householdbudget.app.domain.CategoryKind.INCOME -> "+"
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> "−"
                        com.householdbudget.app.domain.CategoryKind.SAVINGS -> "↓"
                    }
                    val parentPrefix = row.parentCategoryName?.let { "$it · " }.orEmpty()

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding, vertical = 3.dp)
                            .clickable { onTransactionClick(row.id) },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(avatarBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = com.householdbudget.app.ui.util.resolveCategoryDisplay(
                                        leafIcon = row.categoryIcon,
                                        parentIcon = row.parentCategoryIcon,
                                        leafName = row.categoryName,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = avatarTextColor,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "$parentPrefix${row.categoryName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (row.memo.isNotBlank()) {
                                    Text(
                                        text = row.memo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = amountPrefix + row.amountMinor.formatWon(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = amountColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerSummaryTile(
    modifier: Modifier,
    title: String,
    amount: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
