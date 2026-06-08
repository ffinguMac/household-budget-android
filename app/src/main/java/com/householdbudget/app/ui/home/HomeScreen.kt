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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.householdbudget.app.ui.theme.TossBlue
import com.householdbudget.app.ui.theme.TossBlueDark
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
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary.period.formatRangeKorean(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── 순액 히어로 카드 (토스 블루) ──────────────────────────────────────
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        Brush.linearGradient(colors = listOf(TossBlue, TossBlueDark)),
                    ),
        ) {
            // soft decorative glow
            Box(
                modifier =
                    Modifier
                        .size(200.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
            )
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_net),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = netPrefix + absNet.formatWon(),
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = summary.period.formatRangeKorean(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 수입 / 지출 / 저축 3분할 카드 ────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeSummaryCard(
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary,
                label = stringResource(R.string.home_income),
                amount = summary.totalIncomeMinor.formatWon(),
            )
            HomeSummaryCard(
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.error,
                label = stringResource(R.string.home_expense),
                amount = summary.totalExpenseMinor.formatWon(),
            )
            HomeSummaryCard(
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary,
                label = stringResource(R.string.home_savings),
                amount = summary.totalSavingsMinor.formatWon(),
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── 최근 거래 ────────────────────────────────────────────────────────
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val recent = summary.transactions.take(10)
            if (recent.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = stringResource(R.string.ledger_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                    ) {
                        recent.forEach { row ->
                            val d = LocalDate.ofEpochDay(row.occurredEpochDay)
                            val kind = CategoryKind.fromStorage(row.kind)
                            val amountColor =
                                when (kind) {
                                    CategoryKind.INCOME -> MaterialTheme.colorScheme.secondary
                                    CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primary
                                    CategoryKind.EXPENSE -> MaterialTheme.colorScheme.onSurface
                                }
                            val avatarBg =
                                when (kind) {
                                    CategoryKind.INCOME -> MaterialTheme.colorScheme.secondaryContainer
                                    CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primaryContainer
                                    CategoryKind.EXPENSE -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            val avatarTextColor =
                                when (kind) {
                                    CategoryKind.INCOME -> MaterialTheme.colorScheme.onSecondaryContainer
                                    CategoryKind.SAVINGS -> MaterialTheme.colorScheme.onPrimaryContainer
                                    CategoryKind.EXPENSE -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            val amountPrefix =
                                when (kind) {
                                    CategoryKind.INCOME -> "+"
                                    CategoryKind.EXPENSE -> "−"
                                    CategoryKind.SAVINGS -> "↓"
                                }
                            val parentPrefix = row.parentCategoryName?.let { "$it · " }.orEmpty()

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(avatarBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = row.categoryName.take(1),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
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
                                    Text(
                                        text =
                                            buildString {
                                                append(d.format(dateFmt))
                                                if (row.memo.isNotBlank()) append(" · ${row.memo}")
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
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

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun HomeSummaryCard(
    modifier: Modifier,
    accent: Color,
    label: String,
    amount: String,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
