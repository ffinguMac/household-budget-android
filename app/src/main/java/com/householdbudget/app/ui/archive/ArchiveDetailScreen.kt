package com.householdbudget.app.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.util.formatRangeKorean
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ArchiveDetailScreen(
    archiveId: Long,
    repository: BudgetRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var header by remember { mutableStateOf<ArchivedPeriodEntity?>(null) }

    LaunchedEffect(archiveId) {
        header = repository.getArchivedPeriod(archiveId)
    }

    val h = header
    if (h == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(ScreenHorizontalPadding),
        ) {
            Text(stringResource(R.string.archive_missing), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        }
        return
    }

    val txs by
        repository
            .observeTransactionsInRange(h.startEpochDay, h.endEpochDay)
            .collectAsStateWithLifecycle(initialValue = emptyList())
    val dateFmt = DateTimeFormatter.ofPattern("MM.dd").withLocale(Locale.KOREA)

    val period =
        BudgetPeriod(
            startInclusive = LocalDate.ofEpochDay(h.startEpochDay),
            endExclusive = LocalDate.ofEpochDay(h.endEpochDay),
        )

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconButton(
            onClick = onBack,
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recurring_back))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(period.formatRangeKorean(), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        R.string.archive_detail_totals,
                        h.totalIncomeMinor.formatWon(),
                        h.totalExpenseMinor.formatWon(),
                        h.totalSavingsMinor.formatWon(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            stringResource(R.string.archive_detail_transactions),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(txs, key = { it.id }) { row ->
                val d = LocalDate.ofEpochDay(row.occurredEpochDay)
                val kind = com.householdbudget.app.domain.CategoryKind.fromStorage(row.kind)
                val prefix = when (kind) {
                    com.householdbudget.app.domain.CategoryKind.INCOME -> "+"
                    com.householdbudget.app.domain.CategoryKind.EXPENSE -> "-"
                    com.householdbudget.app.domain.CategoryKind.SAVINGS -> "↓"
                }
                val parentPrefix = row.parentCategoryName?.let { "$it · " }.orEmpty()
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(
                        text =
                            "${d.format(dateFmt)} · $parentPrefix${row.categoryName} · " +
                                prefix +
                                row.amountMinor.formatWon() +
                                if (row.memo.isNotBlank()) "\n${row.memo}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
