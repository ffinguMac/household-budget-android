package com.householdbudget.app.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.ui.components.KindSummaryRow
import com.householdbudget.app.ui.components.ScreenHeader
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatRangeShort
import com.householdbudget.app.ui.util.formatWon
import com.householdbudget.app.ui.components.EmptyState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val savedAtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd 보관").withLocale(Locale.KOREA)

/**
 * 아카이브 기간 제목: 시작일의 달 기준 "8월", 올해가 아니면 "2025년 8월".
 * (월급날 기준 한 달은 시작일이 속한 달로 부른다.)
 */
@Composable
internal fun archiveMonthTitle(start: LocalDate, today: LocalDate): String =
    if (start.year == today.year) {
        stringResource(R.string.archive_month_label, start.monthValue)
    } else {
        stringResource(R.string.archive_month_label_with_year, start.year, start.monthValue)
    }

@Composable
fun ArchiveListScreen(
    repository: BudgetRepository,
    onOpenDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by repository.observeArchivedPeriods().collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = Space.xxxl),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.archive_title),
                subtitle = stringResource(R.string.archive_subtitle_short),
                modifier = Modifier.fillMaxWidth(),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val period = BudgetPeriod(
        startInclusive = LocalDate.ofEpochDay(row.startEpochDay),
        endExclusive = LocalDate.ofEpochDay(row.endEpochDay),
    )
    val startDate = LocalDate.ofEpochDay(row.startEpochDay)
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    val periodLabel = archiveMonthTitle(startDate, today)
    val savedAt = Instant.ofEpochMilli(row.archivedAtEpochMs)
        .atZone(ZoneId.of("Asia/Seoul")).toLocalDate()
    val savedAtText = savedAt.format(savedAtFormatter)
    val netMinor = row.totalIncomeMinor - row.totalExpenseMinor - row.totalSavingsMinor

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(Space.xl)) {
            // 기간 제목 + 보관 시각
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = savedAtText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.archive_range_payday_based, period.formatRangeShort()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.lg))

            // 순액(수입 − 지출 − 저축) — "+N원 남김"(민트) / "−N원 초과"(레드)
            Text(
                text = if (netMinor >= 0) {
                    stringResource(R.string.archive_net_left, netMinor.formatWon())
                } else {
                    stringResource(R.string.archive_net_over, (-netMinor).formatWon())
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (netMinor >= 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )

            Spacer(Modifier.height(Space.md))

            // 수입/지출/저축 3분할 요약
            KindSummaryRow(
                incomeMinor = row.totalIncomeMinor,
                expenseMinor = row.totalExpenseMinor,
                savingsMinor = row.totalSavingsMinor,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Space.md))

            Text(
                text = stringResource(R.string.archive_open_detail),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
