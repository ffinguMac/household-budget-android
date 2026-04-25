package com.householdbudget.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.theme.NavyContainer
import com.householdbudget.app.ui.theme.NavyDeep
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
    val netAmountColor = if (summary.netMinor >= 0) Color.White else MaterialTheme.colorScheme.errorContainer

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 헤더 ────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
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

        // ── 순액 카드 (네이비 그라디언트) ───────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(
                    Brush.linearGradient(
                        colors = listOf(NavyContainer, NavyDeep),
                    )
                ),
        ) {
            // decorative circle glow
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.TopEnd)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(NavyDeep.copy(alpha = 0.25f))
            )
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_net),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                        1.5f,
                        androidx.compose.ui.unit.TextUnitType.Sp,
                    ),
                )
                Text(
                    text = netPrefix + absNet.formatWon(),
                    style = MaterialTheme.typography.displaySmall,
                    color = netAmountColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary.period.formatRangeKorean(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 수입 / 지출 / 저축 3분할 카드 ────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeSummaryCard(
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f),
                label = stringResource(R.string.home_income),
                amount = summary.totalIncomeMinor.formatWon(),
            )
            HomeSummaryCard(
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                label = stringResource(R.string.home_expense),
                amount = summary.totalExpenseMinor.formatWon(),
            )
            HomeSummaryCard(
                modifier = Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                label = stringResource(R.string.home_savings),
                amount = summary.totalSavingsMinor.formatWon(),
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── 예산 진행률 (설정된 카테고리가 있을 때만 노출) ───────────────────
        val progress by budgetViewModel.budgetProgress.collectAsStateWithLifecycle()
        if (progress.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_budget_section),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        progress.take(5).forEach { bp ->
                            BudgetProgressRow(bp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── 최근 거래 ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
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
                Text(
                    text = stringResource(R.string.ledger_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        recent.forEach { row ->
                            val d = LocalDate.ofEpochDay(row.occurredEpochDay)
                            val kind = com.householdbudget.app.domain.CategoryKind.fromStorage(row.kind)
                            val amountColor = when (kind) {
                                com.householdbudget.app.domain.CategoryKind.INCOME -> MaterialTheme.colorScheme.secondary
                                com.householdbudget.app.domain.CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primary
                                com.householdbudget.app.domain.CategoryKind.EXPENSE -> MaterialTheme.colorScheme.onSurface
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
                                modifier = Modifier.fillMaxWidth(),
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
                                        Text(
                                            text = buildString {
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
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun BudgetProgressRow(bp: com.householdbudget.app.data.repository.BudgetProgress) {
    val fraction = (bp.spentMinor.toFloat() / bp.monthlyAmountMinor.toFloat()).coerceIn(0f, 1f)
    val barColor = when {
        bp.exceeded -> MaterialTheme.colorScheme.error
        bp.percent >= 80 -> androidx.compose.ui.graphics.Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = com.householdbudget.app.ui.util.resolveCategoryDisplay(
        leafIcon = bp.categoryIcon,
        parentIcon = bp.parentIcon,
        leafName = bp.categoryName,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$icon  " + (bp.parentName?.let { "$it · ${bp.categoryName}" } ?: bp.categoryName),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${bp.spentMinor.formatWon()} / ${bp.monthlyAmountMinor.formatWon()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (bp.exceeded) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { fraction },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small),
        )
    }
}

@Composable
private fun HomeSummaryCard(
    modifier: Modifier,
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    amount: String,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
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
}
