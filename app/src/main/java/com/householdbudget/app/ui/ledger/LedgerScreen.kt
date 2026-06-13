package com.householdbudget.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.householdbudget.app.ui.components.ScreenHeader
import com.householdbudget.app.ui.components.ScreenHorizontalPadding

import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LedgerScreen(
    budgetViewModel: BudgetViewModel,
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by budgetViewModel.transactions.collectAsStateWithLifecycle()
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
            Column {
                ScreenHeader(title = "거래 내역", subtitle = "이번 회계월")
                // 수입 / 지출 / 저축 요약 — 한 덩어리로 차분하게
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding)
                        .padding(bottom = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Row(modifier = Modifier.padding(vertical = 18.dp)) {
                        LedgerSummaryTile(
                            modifier = Modifier.weight(1f),
                            title = "수입",
                            amount = "+${summary.totalIncomeMinor.formatWon()}",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        LedgerSummaryTile(
                            modifier = Modifier.weight(1f),
                            title = "지출",
                            amount = "−${summary.totalExpenseMinor.formatWon()}",
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        LedgerSummaryTile(
                            modifier = Modifier.weight(1f),
                            title = "저축",
                            amount = "↓${summary.totalSavingsMinor.formatWon()}",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
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
                                text = "↑${daySavings.formatWon()}",
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
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> MaterialTheme.colorScheme.onSurface
                    }
                    val amountPrefix = when (kind) {
                        com.householdbudget.app.domain.CategoryKind.INCOME -> "+"
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> "−"
                        com.householdbudget.app.domain.CategoryKind.SAVINGS -> "↓"
                    }
                    val parentPrefix = row.parentCategoryName?.let { "$it · " }.orEmpty()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTransactionClick(row.id) }
                            .padding(horizontal = ScreenHorizontalPadding, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = row.categoryName.take(1),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun LedgerSummaryTile(
    modifier: Modifier,
    title: String,
    amount: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
