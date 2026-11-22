package com.householdbudget.app.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.householdbudget.app.data.repository.HomeSummary
import com.householdbudget.app.domain.BudgetPeriod
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.KindSummaryRow
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader
import com.householdbudget.app.ui.components.TransactionRow
import com.householdbudget.app.ui.components.pushedBottomPadding
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatRangeShort
import com.householdbudget.app.ui.util.formatShortDayLabel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDetailScreen(
    archiveId: Long,
    repository: BudgetRepository,
    paydayDom: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var header by remember { mutableStateOf<ArchivedPeriodEntity?>(null) }
    // header == null 만으로는 "불러오는 중"과 "없음"을 구분할 수 없다.
    var loaded by remember { mutableStateOf(false) }
    var confirmCount by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val today by repository.today.collectAsStateWithLifecycle()

    LaunchedEffect(archiveId) {
        header = repository.getArchivedPeriod(archiveId)
        loaded = true
    }

    val h = header

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(
            title = if (h == null) {
                stringResource(R.string.archive_title)
            } else {
                archiveMonthTitle(LocalDate.ofEpochDay(h.startEpochDay), paydayDom, today)
            },
            onBack = onBack,
            actions = {
                if (h != null) {
                    IconButton(
                        onClick = { scope.launch { confirmCount = repository.countTransactionsIn(h) } },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.archive_delete_month),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        if (h == null) {
            if (!loaded) return@Column
            Box(
                Modifier
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
            return@Column
        }

        // null = 아직 로드 전. 합계는 스냅샷이 아니라 이 목록에서 낸다(지난 거래 수정 반영).
        val txs by
            remember(repository, h.startEpochDay, h.endEpochDay) {
                repository.observeTransactionsInRange(h.startEpochDay, h.endEpochDay)
            }.collectAsStateWithLifecycle(initialValue = null)
        val period = BudgetPeriod(
            startInclusive = LocalDate.ofEpochDay(h.startEpochDay),
            endExclusive = LocalDate.ofEpochDay(h.endEpochDay),
        )
        val loaded = txs
        val summary = remember(period, loaded) { loaded?.let { HomeSummary(period, it) } }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = pushedBottomPadding()),
        ) {
            item {
                Text(
                    text = period.formatRangeShort(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = ScreenHorizontalPadding,
                        vertical = Space.sm,
                    ),
                )
            }

            if (summary == null) return@LazyColumn

            item {
                KindSummaryRow(
                    incomeMinor = summary.totalIncomeMinor,
                    expenseMinor = summary.totalExpenseMinor,
                    savingsMinor = summary.totalSavingsMinor,
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

            items(summary.transactions, key = { it.id }) { row ->
                val kind = CategoryKind.fromStorage(row.kind)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding, vertical = Space.xs),
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

    val count = confirmCount
    val dh = header
    if (count != null && dh != null) {
        val range = BudgetPeriod(LocalDate.ofEpochDay(dh.startEpochDay), LocalDate.ofEpochDay(dh.endEpochDay)).formatRangeShort()
        AlertDialog(
            onDismissRequest = { confirmCount = null },
            title = {
                Text(stringResource(R.string.archive_delete_confirm_title, archiveMonthTitle(LocalDate.ofEpochDay(dh.startEpochDay), paydayDom, today)))
            },
            text = { Text(stringResource(R.string.archive_delete_confirm_body, range, count)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCount = null
                    scope.launch {
                        repository.deleteArchivedPeriod(dh)
                        onBack()
                    }
                }) { Text(stringResource(R.string.archive_delete_confirm_ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCount = null }) { Text(stringResource(R.string.edit_dismiss)) }
            },
        )
    }
}
