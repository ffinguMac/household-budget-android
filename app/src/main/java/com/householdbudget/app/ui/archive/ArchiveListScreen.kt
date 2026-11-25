package com.householdbudget.app.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.PeriodResolver
import com.householdbudget.app.ui.components.FitText
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.cardBorder
import com.householdbudget.app.ui.components.pushedBottomPadding
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatRangeShort
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.YearMonth

/**
 * 기간 제목: 명목 월([PeriodResolver.nominalMonth]) 기준 "8월", 올해가 아니면 "2025년 8월".
 * 시작일의 달로 부르면 앞 달로 당겨진 월급일(1월 1일 → 12월 31일)에서 "12월"이 두 번 나온다.
 */
@Composable
internal fun archiveMonthTitle(start: LocalDate, paydayDom: Int, today: LocalDate): String =
    archiveMonthTitle(periodResolver.nominalMonth(start, paydayDom), today)

/** 상태 없는 계산기라 하나만 두고 제목 호출부가 같이 쓴다. */
internal val periodResolver = PeriodResolver()

@Composable
internal fun archiveMonthTitle(month: YearMonth, today: LocalDate): String =
    if (month.year == today.year) {
        stringResource(R.string.archive_month_label, month.monthValue)
    } else {
        stringResource(R.string.archive_month_label_with_year, month.year, month.monthValue)
    }

@Composable
fun ArchiveListScreen(
    repository: BudgetRepository,
    paydayDom: Int,
    onOpenDetail: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by remember(repository) { repository.observeArchivedPeriods() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val today by repository.today.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = pushedBottomPadding()),
    ) {
        item {
            AppTopBar(title = stringResource(R.string.archive_title), onBack = onBack)
            Text(
                text = stringResource(R.string.archive_subtitle_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = Space.sm),
            )
        }

        if (rows.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Archive,
                    title = stringResource(R.string.archive_empty_title),
                    description = stringResource(R.string.archive_empty_desc),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            itemsIndexed(rows, key = { _, it -> it.id }) { _, row ->
                ArchiveCard(
                    row = row,
                    today = today,
                    paydayDom = paydayDom,
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = Space.xs),
                    onClick = { onOpenDetail(row.id) },
                )
            }
        }
    }
}

@Composable
private fun ArchiveCard(
    row: ArchivedPeriodEntity,
    today: LocalDate,
    paydayDom: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val period = BudgetPeriod(
        startInclusive = LocalDate.ofEpochDay(row.startEpochDay),
        endExclusive = LocalDate.ofEpochDay(row.endEpochDay),
    )
    val periodLabel = stringResource(
        R.string.archive_card_period,
        archiveMonthTitle(period.startInclusive, paydayDom, today),
        period.formatRangeShort(),
    )
    val netMinor = row.totalIncomeMinor - row.totalExpenseMinor - row.totalSavingsMinor

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = cardBorder(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(Space.xl)) {
            Text(
                text = periodLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.xs))
            FitText(
                text = if (netMinor >= 0) {
                    stringResource(R.string.archive_net_left, netMinor.formatWon())
                } else {
                    stringResource(R.string.archive_net_over, (-netMinor).formatWon())
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Space.sm))
            FitText(
                text = stringResource(
                    R.string.archive_detail_totals,
                    row.totalIncomeMinor.formatWon(),
                    row.totalExpenseMinor.formatWon(),
                    row.totalSavingsMinor.formatWon(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
