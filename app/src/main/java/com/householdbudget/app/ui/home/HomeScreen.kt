package com.householdbudget.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ElevatedPanel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader
import com.householdbudget.app.ui.util.formatRangeKorean
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    budgetViewModel: BudgetViewModel,
    modifier: Modifier = Modifier,
) {
    val summary by budgetViewModel.homeSummary.collectAsStateWithLifecycle()
    val dateFmt = DateTimeFormatter.ofPattern("MM.dd").withLocale(Locale.KOREA)

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenHorizontalPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.home_title),
            subtitle = summary.period.formatRangeKorean(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                title = stringResource(R.string.home_income),
                value = summary.totalIncomeMinor.formatWon(),
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = stringResource(R.string.home_expense),
                value = summary.totalExpenseMinor.formatWon(),
                valueColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }

        SummaryCard(
            title = stringResource(R.string.home_net),
            value = summary.netMinor.formatWon(),
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            emphasized = true,
        )

        ElevatedPanel {
            Text(
                text = stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val recent = summary.transactions.take(8)
            if (recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.ledger_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                recent.forEachIndexed { index, row ->
                    val d = LocalDate.ofEpochDay(row.occurredEpochDay)
                    Text(
                        text =
                            "${d.format(dateFmt)} · ${row.categoryName} · " +
                                (if (row.isIncome) "+" else "-") +
                                row.amountMinor.formatWon(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (index < recent.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val container =
        if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val onContainer =
        if (emphasized) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Card(
        modifier = modifier,
        shape =
            if (emphasized) {
                MaterialTheme.shapes.large
            } else {
                RoundedCornerShape(20.dp)
            },
        colors = CardDefaults.cardColors(containerColor = container),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = if (emphasized) 3.dp else 2.dp,
            ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (emphasized) {
                        onContainer.copy(alpha = 0.88f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color =
                    if (emphasized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        valueColor
                    },
            )
        }
    }
}
