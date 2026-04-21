package com.householdbudget.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.theme.DeepDark
import com.householdbudget.app.ui.theme.DarkSurface
import com.householdbudget.app.ui.theme.StoneGray
import com.householdbudget.app.ui.theme.WarmSilver
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
    val dateFmt = DateTimeFormatter.ofPattern("MM.dd (E)").withLocale(Locale.KOREA)

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Dark hero section ─────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DeepDark,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = ScreenHorizontalPadding,
                    vertical = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = summary.period.formatRangeKorean(),
                    style = MaterialTheme.typography.labelLarge,
                    color = StoneGray,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.home_net),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarmSilver,
                )

                val netColor =
                    if (summary.netMinor >= 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                val netPrefix = if (summary.netMinor >= 0) "+" else "-"
                val absNet = if (summary.netMinor < 0) -summary.netMinor else summary.netMinor

                Text(
                    text = netPrefix + absNet.formatWon(),
                    style = MaterialTheme.typography.displaySmall,
                    color = netColor,
                )

                Spacer(Modifier.height(20.dp))

                HorizontalDivider(color = DarkSurface, thickness = 1.dp)

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.home_income),
                            style = MaterialTheme.typography.labelMedium,
                            color = StoneGray,
                        )
                        Text(
                            text = summary.totalIncomeMinor.formatWon(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_expense),
                            style = MaterialTheme.typography.labelMedium,
                            color = StoneGray,
                        )
                        Text(
                            text = summary.totalExpenseMinor.formatWon(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // ── Parchment body ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            val recent = summary.transactions.take(10)
            if (recent.isEmpty()) {
                Text(
                    text = stringResource(R.string.ledger_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.large,
                        ),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                    ) {
                        recent.forEachIndexed { index, row ->
                            val d = LocalDate.ofEpochDay(row.occurredEpochDay)
                            val amountColor =
                                if (row.isIncome) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = row.categoryName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = d.format(dateFmt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = (if (row.isIncome) "+" else "−") + row.amountMinor.formatWon(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = amountColor,
                                )
                            }

                            if (index < recent.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = 1.dp,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
