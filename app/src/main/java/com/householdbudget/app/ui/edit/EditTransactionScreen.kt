@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.householdbudget.app.ui.edit

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.CashbackChannel
import com.householdbudget.app.ui.EditTransactionViewModel
import com.householdbudget.app.ui.EditTransactionViewModelFactory
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.util.formatWon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun EditTransactionScreen(
    budgetViewModel: BudgetViewModel,
    repository: BudgetRepository,
    transactionId: Long?,
    onClose: () -> Unit,
) {
    val vm: EditTransactionViewModel =
        viewModel(
            factory = EditTransactionViewModelFactory(repository, transactionId),
            key = "${transactionId ?: "new"}",
        )
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val parentsByKind by budgetViewModel.parentsByKind.collectAsStateWithLifecycle()
    val childrenByParent by budgetViewModel.childrenByParent.collectAsStateWithLifecycle()
    val kbankCardEnabled by budgetViewModel.kbankCardEnabled.collectAsStateWithLifecycle()
    val zone = ZoneId.of("Asia/Seoul")
    var cashbackChannel by remember { mutableStateOf(CashbackChannel.OFFLINE) }
    var cashbackApply by remember { mutableStateOf(true) }
    val showCashbackSelector = ui.kind == CategoryKind.EXPENSE && kbankCardEnabled && transactionId == null

    val parents = parentsByKind[ui.kind].orEmpty()
    val leaves = ui.parentId?.let { childrenByParent[it] }.orEmpty()

    var showAddParentDialog by remember { mutableStateOf(false) }
    var showAddLeafDialog by remember { mutableStateOf(false) }

    // 최초 로드 또는 kind 변경 시 대분류/소분류 자동 선택.
    LaunchedEffect(parents, leaves, ui.parentId, ui.categoryId, ui.loadFinished) {
        if (!ui.loadFinished) return@LaunchedEffect
        val currentParentId = ui.parentId
        val pickedParent =
            if (currentParentId != null && parents.any { it.id == currentParentId }) currentParentId
            else parents.firstOrNull()?.id
        if (pickedParent == null) return@LaunchedEffect
        val leavesForPicked = childrenByParent[pickedParent].orEmpty()
        val currentLeafId = ui.categoryId
        val pickedLeaf =
            if (currentLeafId != null && leavesForPicked.any { it.id == currentLeafId }) {
                leavesForPicked.first { it.id == currentLeafId }
            } else {
                leavesForPicked.firstOrNull()
            }
        if (pickedParent != currentParentId || pickedLeaf?.id != currentLeafId) {
            vm.setParent(pickedParent, pickedLeaf)
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showInvalid by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (transactionId == null) {
                            stringResource(R.string.edit_title_new)
                        } else {
                            stringResource(R.string.edit_title_edit)
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.large,
                    ),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ── 종류 선택 (수입 / 지출 / 저축) ──
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KindChip(
                            selected = ui.kind == CategoryKind.INCOME,
                            label = stringResource(R.string.edit_income),
                            onClick = { vm.setKind(CategoryKind.INCOME) },
                        )
                        KindChip(
                            selected = ui.kind == CategoryKind.EXPENSE,
                            label = stringResource(R.string.edit_expense),
                            onClick = { vm.setKind(CategoryKind.EXPENSE) },
                        )
                        KindChip(
                            selected = ui.kind == CategoryKind.SAVINGS,
                            label = stringResource(R.string.edit_savings),
                            onClick = { vm.setKind(CategoryKind.SAVINGS) },
                        )
                    }

                    OutlinedTextField(
                        value = ui.amountText,
                        onValueChange = vm::setAmountText,
                        label = { Text(stringResource(R.string.edit_amount)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                    )

                    // ── 대분류 ──
                    Text(
                        text = stringResource(R.string.edit_category_parent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(parents, key = { it.id }) { parent ->
                            val label =
                                if (!parent.icon.isNullOrBlank()) "${parent.icon} ${parent.name}"
                                else parent.name
                            FilterChip(
                                selected = ui.parentId == parent.id,
                                onClick = {
                                    val first = childrenByParent[parent.id]?.firstOrNull()
                                    vm.setParent(parent.id, first)
                                },
                                label = { Text(label) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor =
                                            MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor =
                                            MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                            )
                        }
                        item(key = "__add_parent__") {
                            AssistChip(
                                onClick = { showAddParentDialog = true },
                                label = { Text(stringResource(R.string.edit_add_parent_inline)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }

                    // ── 소분류 ──
                    Text(
                        text = stringResource(R.string.edit_category_child),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ui.parentId == null) {
                        Text(
                            text = stringResource(R.string.edit_category_pick_parent_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(leaves, key = { it.id }) { leaf ->
                                val label =
                                    if (!leaf.icon.isNullOrBlank()) "${leaf.icon} ${leaf.name}"
                                    else leaf.name
                                FilterChip(
                                    selected = ui.categoryId == leaf.id,
                                    onClick = { vm.setCategoryId(leaf.id) },
                                    label = { Text(label) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor =
                                                MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor =
                                                MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                )
                            }
                            item(key = "__add_leaf__") {
                                AssistChip(
                                    onClick = { showAddLeafDialog = true },
                                    label = { Text(stringResource(R.string.edit_add_child_inline)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = ui.memo,
                        onValueChange = vm::setMemo,
                        label = { Text(stringResource(R.string.edit_memo)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                    )

                    if (showCashbackSelector) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.edit_cashback_apply),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = cashbackApply,
                                onCheckedChange = { cashbackApply = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                        if (cashbackApply) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = cashbackChannel == CashbackChannel.OFFLINE,
                                    onClick = { cashbackChannel = CashbackChannel.OFFLINE },
                                    label = { Text(stringResource(R.string.edit_cashback_offline)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                                FilterChip(
                                    selected = cashbackChannel == CashbackChannel.ONLINE,
                                    onClick = { cashbackChannel = CashbackChannel.ONLINE },
                                    label = { Text(stringResource(R.string.edit_cashback_online)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                            val rate = if (cashbackChannel == CashbackChannel.ONLINE) 11L else 6L
                            val previewAmount = (ui.amountText.toLongOrNull() ?: 0L) * rate / 1000L
                            if (previewAmount > 0L) {
                                Text(
                                    text = stringResource(R.string.edit_cashback_preview, previewAmount.formatWon()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text("${stringResource(R.string.edit_date)}: ${ui.date}")
                    }
                }
            }

            if (showInvalid) {
                Text(
                    text = stringResource(R.string.edit_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    vm.save(
                        cashbackChannel = if (showCashbackSelector && cashbackApply) cashbackChannel else null,
                        onSuccess = onClose,
                        onInvalid = { showInvalid = true },
                    )
                },
                enabled = !ui.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(stringResource(R.string.edit_save))
            }

            if (transactionId != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !ui.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.edit_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val millis = ui.date.atStartOfDay(zone).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = state.selectedDateMillis
                        if (selected != null) {
                            val picked =
                                Instant.ofEpochMilli(selected).atZone(zone).toLocalDate()
                            vm.setDate(picked)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.edit_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.edit_dismiss))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showAddParentDialog) {
        CategoryNameDialog(
            title = stringResource(R.string.edit_add_parent_inline),
            onDismiss = { showAddParentDialog = false },
            onConfirm = { name ->
                showAddParentDialog = false
                vm.addParent(name, ui.kind) { result ->
                    result.onSuccess { newParentId -> vm.setParent(newParentId, null) }
                }
            },
        )
    }

    if (showAddLeafDialog) {
        val parentId = ui.parentId
        if (parentId == null) {
            showAddLeafDialog = false
        } else {
            CategoryNameDialog(
                title = stringResource(R.string.edit_add_child_inline),
                onDismiss = { showAddLeafDialog = false },
                onConfirm = { name ->
                    showAddLeafDialog = false
                    vm.addLeaf(parentId, name) { result ->
                        result.onSuccess { newLeafId -> vm.setCategoryId(newLeafId) }
                    }
                },
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.edit_delete_confirm_title)) },
            text = { Text(stringResource(R.string.edit_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.delete(onSuccess = onClose)
                    },
                ) {
                    Text(stringResource(R.string.edit_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.edit_dismiss))
                }
            },
        )
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.edit_new_category_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.trim().isNotEmpty()) onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty(),
            ) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_dismiss)) }
        },
    )
}

@Composable
private fun KindChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    )
}
