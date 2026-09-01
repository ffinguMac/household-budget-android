package com.householdbudget.app.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.components.KindSummaryRow
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatRangeKorean
import com.householdbudget.app.ui.util.formatShortDayLabel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.archive_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.recurring_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner ->
        if (h == null) {
            Box(
                Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(ScreenHorizontalPadding),
            ) {
                Text(
                    text = stringResource(R.string.archive_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            return@Scaffold
        }

        val txs by
            repository
                .observeTransactionsInRange(h.startEpochDay, h.endEpochDay)
                .collectAsStateWithLifecycle(initialValue = emptyList())

        val period = BudgetPeriod(
            startInclusive = LocalDate.ofEpochDay(h.startEpochDay),
            endExclusive = LocalDate.ofEpochDay(h.endEpochDay),
        )

        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = Space.xxxl),
        ) {
            item {
                Text(
                    text = period.formatRangeKorean(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        horizontal = ScreenHorizontalPadding,
                        vertical = Space.sm,
                    ),
                )
            }

            item {
                KindSummaryRow(
                    incomeMinor = h.totalIncomeMinor,
                    expenseMinor = h.totalExpenseMinor,
                    savingsMinor = h.totalSavingsMinor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding),
                )
                Spacer(Modifier.height(Space.xl))
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.archive_detail_transactions),
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
                Spacer(Modifier.height(Space.sm))
            }

            items(txs, key = { it.id }) { row ->
                val kind = CategoryKind.fromStorage(row.kind)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding, vertical = Space.xxs),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    TransactionRow(
                        categoryName = row.categoryName,
                        parentCategoryName = row.parentCategoryName,
                        memo = row.memo,
                        amountMinor = row.amountMinor,
                        kind = kind,
                        dateLabel = LocalDate.ofEpochDay(row.occurredEpochDay).formatShortDayLabel(),
                        onClick = null,
                    )
                }
            }
        }
    }
}
