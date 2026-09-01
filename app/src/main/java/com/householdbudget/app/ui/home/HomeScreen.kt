package com.householdbudget.app.ui.home

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.FabContentBottomPadding
import com.householdbudget.app.ui.components.KindSummaryRow
import com.householdbudget.app.ui.components.ScreenHeader
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.theme.heroGradient
import com.householdbudget.app.ui.theme.onHeroColor
import com.householdbudget.app.ui.theme.onHeroMutedColor
import com.householdbudget.app.ui.util.formatRangeKorean
import com.householdbudget.app.ui.util.formatShortDayLabel
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(
    budgetViewModel: BudgetViewModel,
    modifier: Modifier = Modifier,
    onSeeAllTransactions: (() -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
) {
    val summary by budgetViewModel.homeSummary.collectAsStateWithLifecycle()
    val loaded by budgetViewModel.homeSummaryLoaded.collectAsStateWithLifecycle()
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    val daysLeft =
        ChronoUnit.DAYS
            .between(today, summary.period.endExclusive)
            .coerceAtLeast(0L)

    val netPrefix = if (summary.netMinor >= 0) "+" else "−"
    val absNet = if (summary.netMinor < 0) -summary.netMinor else summary.netMinor

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState),
    ) {
        // ScreenHeader 는 좌우/상하 패딩을 스스로 처리한다.
        ScreenHeader(title = stringResource(R.string.home_title))

        // ── 순액 히어로 카드 ─────────────────────────────────────────────────
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(heroGradient()),
        ) {
            // soft decorative glow
            Box(
                modifier =
                    Modifier
                        .size(200.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(onHeroColor().copy(alpha = 0.08f)),
            )
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_net),
                    style = MaterialTheme.typography.titleSmall,
                    color = onHeroMutedColor(),
                    fontWeight = FontWeight.SemiBold,
                )
                if (loaded) {
                    Text(
                        text = netPrefix + absNet.formatWon(),
                        style = MaterialTheme.typography.displaySmall,
                        color = onHeroColor(),
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .width(180.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(onHeroColor().copy(alpha = 0.16f)),
                    )
                }
                Text(
                    text =
                        summary.period.formatRangeKorean() +
                            " · " +
                            stringResource(R.string.home_days_left, daysLeft),
                    style = MaterialTheme.typography.bodySmall,
                    color = onHeroMutedColor(),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 수입 / 지출 / 저축 3분할 요약 ────────────────────────────────────
        if (loaded) {
            KindSummaryRow(
                incomeMinor = summary.totalIncomeMinor,
                expenseMinor = summary.totalExpenseMinor,
                savingsMinor = summary.totalSavingsMinor,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding),
            )
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding),
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

        Spacer(Modifier.height(28.dp))

        // ── 최근 거래 ────────────────────────────────────────────────────────
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                title = stringResource(R.string.home_recent),
                trailing =
                    onSeeAllTransactions?.let { seeAll ->
                        {
                            TextButton(onClick = seeAll) {
                                Text(text = stringResource(R.string.home_see_all))
                            }
                        }
                    },
            )

            val recent = summary.transactions.take(10)
            if (recent.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = stringResource(R.string.home_empty_title),
                    description = stringResource(R.string.home_empty_desc),
                    actionLabel =
                        if (onSeeAllTransactions != null) {
                            stringResource(R.string.home_see_all)
                        } else {
                            null
                        },
                    onAction = onSeeAllTransactions,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                            TransactionRow(
                                categoryName = row.categoryName,
                                parentCategoryName = row.parentCategoryName,
                                memo = row.memo,
                                amountMinor = row.amountMinor,
                                kind = CategoryKind.fromStorage(row.kind),
                                dateLabel =
                                    LocalDate
                                        .ofEpochDay(row.occurredEpochDay)
                                        .formatShortDayLabel(),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(FabContentBottomPadding))
    }
}
