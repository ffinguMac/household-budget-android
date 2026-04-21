package com.householdbudget.app.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader
import com.householdbudget.app.ui.util.formatRangeKorean
import com.householdbudget.app.ui.util.formatWon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val archiveSavedAtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withLocale(Locale.KOREA)

@Composable
fun ArchiveListScreen(
    repository: BudgetRepository,
    onOpenDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by repository.observeArchivedPeriods().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = ScreenHorizontalPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.archive_title),
            subtitle = stringResource(R.string.archive_subtitle),
        )

        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.archive_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rows, key = { it.id }) { row ->
                    ArchiveRowCard(
                        row = row,
                        onClick = { onOpenDetail(row.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveRowCard(
    row: ArchivedPeriodEntity,
    onClick: () -> Unit,
) {
    val period =
        BudgetPeriod(
            startInclusive = LocalDate.ofEpochDay(row.startEpochDay),
            endExclusive = LocalDate.ofEpochDay(row.endEpochDay),
        )
    val savedAt =
        Instant.ofEpochMilli(row.archivedAtEpochMs).atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(period.formatRangeKorean(), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.archive_row_income, row.totalIncomeMinor.formatWon()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.archive_row_expense, row.totalExpenseMinor.formatWon()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.archive_saved_at, savedAt.format(archiveSavedAtFormatter)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
