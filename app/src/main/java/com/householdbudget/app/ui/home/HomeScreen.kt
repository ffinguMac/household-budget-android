package com.householdbudget.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
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

    val netPrefix = if (summary.netMinor >= 0) "+" else "−"
    val absNet = if (summary.netMinor < 0) -summary.netMinor else summary.netMinor
    val netColor =
        if (summary.netMinor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 헤더 ────────────────────────────────────────────────────────────
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding)
                    .padding(top = 28.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary.period.formatRangeKorean(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── 순잉여 (플랫, 큰 숫자) ────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_net),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = netPrefix + absNet.formatWon(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = netColor,
            )
        }

        Spacer(Modifier.height(32.dp))

        // ── 수입 / 지출 / 저축 (한 덩어리, 점 없이) ─────────────────────────────
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SummaryRow(
                    label = stringResource(R.string.home_income),
                    amount = summary.totalIncomeMinor.formatWon(),
                    amountColor = MaterialTheme.colorScheme.secondary,
                )
                ThinDivider()
                SummaryRow(
                    label = stringResource(R.string.home_expense),
                    amount = summary.totalExpenseMinor.formatWon(),
                    amountColor = MaterialTheme.colorScheme.onSurface,
                )
                ThinDivider()
                SummaryRow(
                    label = stringResource(R.string.home_savings),
                    amount = summary.totalSavingsMinor.formatWon(),
                    amountColor = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        // ── 최근 거래 ────────────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.home_recent),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
        )

        Spacer(Modifier.height(8.dp))

        val recent = summary.transactions.take(10)
        if (recent.isEmpty()) {
            Text(
                text = stringResource(R.string.ledger_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
            )
        } else {
            recent.forEach { row ->
                val d = LocalDate.ofEpochDay(row.occurredEpochDay)
                val kind = CategoryKind.fromStorage(row.kind)
                val amountColor =
                    when (kind) {
                        CategoryKind.INCOME -> MaterialTheme.colorScheme.secondary
                        CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primary
                        CategoryKind.EXPENSE -> MaterialTheme.colorScheme.onSurface
                    }
                val amountPrefix =
                    when (kind) {
                        CategoryKind.INCOME -> "+"
                        CategoryKind.EXPENSE -> "−"
                        CategoryKind.SAVINGS -> "↓"
                    }
                val parentPrefix = row.parentCategoryName?.let { "$it · " }.orEmpty()
                val secondary =
                    buildString {
                        append(d.format(dateFmt))
                        if (row.memo.isNotBlank()) append(" · ${row.memo}")
                    }

                TransactionRow(
                    initial = row.categoryName.take(1),
                    title = "$parentPrefix${row.categoryName}",
                    subtitle = secondary,
                    amount = amountPrefix + row.amountMinor.formatWon(),
                    amountColor = amountColor,
                )
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SummaryRow(
    label: String,
    amount: String,
    amountColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor,
        )
    }
}

@Composable
private fun ThinDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun TransactionRow(
    initial: String,
    title: String,
    subtitle: String,
    amount: String,
    amountColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
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
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = amount,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = amountColor,
        )
    }
}
