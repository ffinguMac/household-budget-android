package com.householdbudget.app.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.CashEntryEntity
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.data.repository.CashResult
import com.householdbudget.app.data.repository.MAX_CASH_MEMO
import com.householdbudget.app.ui.components.FitText
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.cardBorder
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.GroupedDigitsTransformation
import com.householdbudget.app.ui.util.formatMonthDayWeekday
import com.householdbudget.app.ui.util.formatWon
import com.householdbudget.app.ui.util.stripDigits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val rowDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M.d")
private val rowDateWithYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yy.M.d")

/** 시트 대상: 새 넣기/빼기(entry = null) 또는 기존 기록 고치기. */
private data class CashSheet(val deposit: Boolean, val entry: CashEntryEntity? = null)

@Composable
fun CashReserveScreen(
    repository: BudgetRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val balance by remember(repository) { repository.observeCashBalance() }.collectAsStateWithLifecycle(initialValue = 0L)
    val entries by remember(repository) { repository.observeCashEntries() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val today by repository.today.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<CashSheet?>(null) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(title = stringResource(R.string.cash_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Space.lg),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHorizontalPadding),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = cardBorder(),
                ) {
                    Column(modifier = Modifier.padding(Space.lg)) {
                        Text(
                            text = stringResource(R.string.cash_balance_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FitText(
                            text = balance.formatWon(),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(Space.lg))
            }
            if (entries.isEmpty()) {
                item { EmptyState(icon = Icons.Filled.Savings, title = stringResource(R.string.cash_empty)) }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHorizontalPadding),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = cardBorder(),
                    ) {
                        Column {
                            entries.forEachIndexed { index, entry ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Space.lg),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                CashEntryRow(entry, today) { sheet = CashSheet(deposit = entry.amountMinor > 0, entry = entry) }
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = ScreenHorizontalPadding, vertical = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Button(
                onClick = { sheet = CashSheet(deposit = true) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.cash_deposit)) }
            OutlinedButton(
                onClick = { sheet = CashSheet(deposit = false) },
                enabled = balance > 0,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.cash_withdraw)) }
        }
    }

    sheet?.let { target ->
        CashEntrySheet(repository = repository, target = target, today = today, onDismiss = { sheet = null })
    }
}

@Composable
private fun CashEntryRow(entry: CashEntryEntity, today: LocalDate, onClick: () -> Unit) {
    val deposit = entry.amountMinor > 0
    val day = LocalDate.ofEpochDay(entry.occurredEpochDay)
    val spokenDate = day.formatMonthDayWeekday()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.cash_edit_action), role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.format(if (day.year == today.year) rowDateFormatter else rowDateWithYearFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // "11.25"를 소수로 읽지 않게 한다.
            modifier = Modifier.semantics { contentDescription = spokenDate },
        )
        Text(
            text = entry.memo.ifEmpty { stringResource(if (deposit) R.string.cash_deposit else R.string.cash_withdraw) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            // 금액이 길어지면 메모가 먼저 줄어든다.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (deposit) {
                stringResource(R.string.cash_amount_plus, entry.amountMinor.formatWon())
            } else {
                stringResource(R.string.cash_amount_minus, (-entry.amountMinor).formatWon())
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (deposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashEntrySheet(
    repository: BudgetRepository,
    target: CashSheet,
    today: LocalDate,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val entry = target.entry
    var amountText by remember { mutableStateOf(entry?.let { kotlin.math.abs(it.amountMinor).toString() }.orEmpty()) }
    var memo by remember { mutableStateOf(entry?.memo.orEmpty()) }
    var date by remember { mutableStateOf(entry?.let { LocalDate.ofEpochDay(it.occurredEpochDay) } ?: today) }
    var amountErrorRes by remember { mutableStateOf<Int?>(null) }
    var deleteError by remember { mutableStateOf(false) }
    // 두 번 눌러 두 번 저장·삭제되지 않게, 호출이 끝날 때까지 버튼을 막는다.
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val amount = amountText.toLongOrNull() ?: 0L

    fun save() {
        if (saving) return
        saving = true
        val signed = if (target.deposit) amount else -amount
        scope.launch {
            val result = if (entry == null) {
                repository.addCashEntry(date, signed, memo)
            } else {
                repository.updateCashEntry(entry.id, date, signed, memo)
            }
            if (result == CashResult.SAVED) {
                onDismiss()
            } else {
                // 넣기를 줄여서 모자란 경우는 "많이 뺄 수 없어요"가 맞지 않는다.
                amountErrorRes = when {
                    result == CashResult.INVALID -> R.string.cash_error_invalid
                    target.deposit -> R.string.cash_error_shrink
                    else -> R.string.cash_error_overdrawn
                }
                saving = false
            }
        }
    }

    fun delete(id: Long) {
        if (saving) return
        saving = true
        scope.launch {
            if (repository.deleteCashEntry(id) == CashResult.OVERDRAWN) {
                deleteError = true
                saving = false
            } else {
                onDismiss()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(bottom = Space.xxxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = stringResource(if (target.deposit) R.string.cash_sheet_deposit else R.string.cash_sheet_withdraw),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = amountText,
                // 앞자리 0은 지우고, 콤마는 화면에서만 붙인다(커서 어긋남 방지).
                onValueChange = {
                    amountText = stripDigits(it).trimStart('0').take(12)
                    amountErrorRes = null
                    deleteError = false
                },
                visualTransformation = GroupedDigitsTransformation,
                label = { Text(stringResource(R.string.edit_amount)) },
                singleLine = true,
                isError = amountErrorRes != null,
                supportingText = amountErrorRes?.let { res -> { Text(stringResource(res)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it.take(MAX_CASH_MEMO) },
                label = { Text(stringResource(R.string.edit_memo_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            )
            AssistChip(
                onClick = { showDatePicker = true },
                label = { Text(date.formatMonthDayWeekday()) },
                leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.cash_change_date)) },
            )
            if (deleteError) {
                Text(
                    text = stringResource(R.string.cash_error_delete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (entry != null) {
                    TextButton(onClick = { showDeleteConfirm = true }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { save() },
                    enabled = amount > 0 && !saving,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.edit_save)) }
            }
        }
    }

    if (showDatePicker) {
        val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() // DatePicker 는 UTC 기준
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.edit_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.edit_dismiss)) }
            },
        ) { DatePicker(state = state) }
    }

    if (showDeleteConfirm && entry != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.edit_delete_confirm_title)) },
            text = { Text(stringResource(R.string.edit_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        delete(entry.id)
                    },
                    enabled = !saving,
                ) { Text(stringResource(R.string.edit_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.edit_dismiss)) }
            },
        )
    }
}
