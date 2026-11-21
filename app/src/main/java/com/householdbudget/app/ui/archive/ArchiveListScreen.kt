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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val rows by remember(repository) { repository.observeArchivedPeriods() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val today by repository.today.collectAsStateWithLifecycle()
    val period by repository.currentPeriod.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var pickDate by remember { mutableStateOf(false) }
    // (기준 날짜, 거래 수, 지난 기록 수) — 삭제 확인 창에 보여 준다.
    var confirm by remember { mutableStateOf<Triple<LocalDate, Int, Int>?>(null) }

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
            TextButton(
                onClick = { pickDate = true },
                enabled = period != null,
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            ) {
                Text(stringResource(R.string.archive_delete_old), color = MaterialTheme.colorScheme.error)
            }
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
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = Space.xs),
                    onClick = { onOpenDetail(row.id) },
                )
            }
        }
    }
    DeleteOldRecordsDialogs(
        repository = repository,
        defaultDate = period?.startInclusive,
        today = today,
        pickDate = pickDate,
        onPickDateChange = { pickDate = it },
        confirm = confirm,
        onConfirmChange = { confirm = it },
        scope = scope,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteOldRecordsDialogs(
    repository: BudgetRepository,
    defaultDate: LocalDate?,
    today: LocalDate,
    pickDate: Boolean,
    onPickDateChange: (Boolean) -> Unit,
    confirm: Triple<LocalDate, Int, Int>?,
    onConfirmChange: (Triple<LocalDate, Int, Int>?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (pickDate && defaultDate != null) {
        // DatePicker 는 UTC 자정 millis 기준
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = defaultDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            // 미래 날짜를 고르면 오늘 기록까지 지워지므로 막는다.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { onPickDateChange(false) },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    onPickDateChange(false)
                    if (millis != null) {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        scope.launch {
                            val (tx, periods) = repository.countRecordsBefore(date)
                            onConfirmChange(Triple(date, tx, periods))
                        }
                    }
                }) { Text(stringResource(R.string.archive_delete_next)) }
            },
            dismissButton = {
                TextButton(onClick = { onPickDateChange(false) }) { Text(stringResource(R.string.edit_dismiss)) }
            },
        ) {
            Text(
                stringResource(R.string.archive_delete_pick_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = Space.xl, top = Space.lg),
            )
            DatePicker(state = state, title = null)
        }
    }
    confirm?.let { (date, tx, periods) ->
        AlertDialog(
            onDismissRequest = { onConfirmChange(null) },
            title = { Text(stringResource(R.string.archive_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.archive_delete_confirm_body, date.monthValue, date.dayOfMonth, tx, periods))
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmChange(null)
                    scope.launch { repository.deleteRecordsBefore(date) }
                }) { Text(stringResource(R.string.archive_delete_confirm_ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmChange(null) }) { Text(stringResource(R.string.edit_dismiss)) }
            },
        )
    }
}

@Composable
private fun ArchiveCard(
    row: ArchivedPeriodEntity,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val period = BudgetPeriod(
        startInclusive = LocalDate.ofEpochDay(row.startEpochDay),
        endExclusive = LocalDate.ofEpochDay(row.endEpochDay),
    )
    val startDate = LocalDate.ofEpochDay(row.startEpochDay)
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
