package com.householdbudget.app.ui.settings.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.CategoryEntity
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.data.repository.CategoryValidationError
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.components.ScreenHorizontalPadding

@Composable
fun CategoryManagementScreen(
    repository: BudgetRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: CategoryManagementViewModel =
        viewModel(factory = CategoryManagementViewModelFactory(repository))
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()

    val filtered = remember(ui.selectedKind, groups) {
        groups.filter { it.parent.kind == ui.selectedKind.storage }
    }

    var showAddParent by remember { mutableStateOf(false) }
    var addChildForParent by remember { mutableStateOf<CategoryEntity?>(null) }
    var renaming by remember { mutableStateOf<CategoryEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    var editingBudgetFor by remember { mutableStateOf<CategoryEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddParent = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.category_add_parent)) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
    ) { inner ->
        Column(
            modifier
                .padding(inner)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.category_management_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Kind 탭
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KindTab(
                    selected = ui.selectedKind == CategoryKind.INCOME,
                    label = stringResource(R.string.category_tab_income),
                    onClick = { vm.selectKind(CategoryKind.INCOME) },
                )
                KindTab(
                    selected = ui.selectedKind == CategoryKind.EXPENSE,
                    label = stringResource(R.string.category_tab_expense),
                    onClick = { vm.selectKind(CategoryKind.EXPENSE) },
                )
                KindTab(
                    selected = ui.selectedKind == CategoryKind.SAVINGS,
                    label = stringResource(R.string.category_tab_savings),
                    onClick = { vm.selectKind(CategoryKind.SAVINGS) },
                )
            }

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.category_empty_for_kind),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = ScreenHorizontalPadding,
                        end = ScreenHorizontalPadding,
                        bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { "p_${it.parent.id}" }) { group ->
                        ParentRow(
                            group = group,
                            isExpanded = group.parent.id in ui.expanded,
                            budgets = budgets,
                            onToggle = { vm.toggleExpanded(group.parent.id) },
                            onRename = { renaming = group.parent },
                            onDelete = { confirmDelete = group.parent },
                            onAddChild = { addChildForParent = group.parent },
                            onRenameChild = { leaf -> renaming = leaf },
                            onDeleteChild = { leaf -> confirmDelete = leaf },
                            onEditBudget = { leaf -> editingBudgetFor = leaf },
                            onMoveChildUp = { leaf ->
                                val ids = group.children.map { it.id }.toMutableList()
                                val idx = ids.indexOf(leaf.id)
                                if (idx > 0) {
                                    ids.removeAt(idx); ids.add(idx - 1, leaf.id)
                                    vm.reorderChildren(group.parent.id, ids)
                                }
                            },
                            onMoveChildDown = { leaf ->
                                val ids = group.children.map { it.id }.toMutableList()
                                val idx = ids.indexOf(leaf.id)
                                if (idx in 0 until ids.size - 1) {
                                    ids.removeAt(idx); ids.add(idx + 1, leaf.id)
                                    vm.reorderChildren(group.parent.id, ids)
                                }
                            },
                            onMoveParentUp = {
                                val ids = filtered.map { it.parent.id }.toMutableList()
                                val idx = ids.indexOf(group.parent.id)
                                if (idx > 0) {
                                    ids.removeAt(idx); ids.add(idx - 1, group.parent.id)
                                    vm.reorderParents(ui.selectedKind, ids)
                                }
                            },
                            onMoveParentDown = {
                                val ids = filtered.map { it.parent.id }.toMutableList()
                                val idx = ids.indexOf(group.parent.id)
                                if (idx in 0 until ids.size - 1) {
                                    ids.removeAt(idx); ids.add(idx + 1, group.parent.id)
                                    vm.reorderParents(ui.selectedKind, ids)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    ui.error?.let { err ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text(validationTitle(err)) },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text(stringResource(R.string.category_cancel))
                }
            },
        )
    }

    if (showAddParent) {
        NamePromptDialog(
            title = stringResource(R.string.category_add_parent),
            initial = "",
            onDismiss = { showAddParent = false },
            onConfirm = { name ->
                vm.addParent(name)
                showAddParent = false
            },
        )
    }

    addChildForParent?.let { parent ->
        NamePromptDialog(
            title = "${parent.name} · ${stringResource(R.string.category_add_child)}",
            initial = "",
            onDismiss = { addChildForParent = null },
            onConfirm = { name ->
                vm.addChild(parent.id, name)
                addChildForParent = null
            },
        )
    }

    renaming?.let { target ->
        NamePromptDialog(
            title = stringResource(R.string.category_rename),
            initial = target.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                vm.rename(target.id, name)
                renaming = null
            },
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.category_delete_confirm_title)) },
            text = { Text("${target.name} — ${stringResource(R.string.category_delete_confirm_simple)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.requestDelete(target)
                    confirmDelete = null
                }) { Text(stringResource(R.string.category_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.category_cancel))
                }
            },
        )
    }

    ui.pendingDeletion?.let { pending ->
        AlertDialog(
            onDismissRequest = { vm.dismissPendingDeletion() },
            title = { Text(stringResource(R.string.category_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.category_delete_with_refs,
                        pending.transactionCount,
                        pending.recurringCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmForceDelete() }) {
                    Text(stringResource(R.string.category_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissPendingDeletion() }) {
                    Text(stringResource(R.string.category_cancel))
                }
            },
        )
    }

    editingBudgetFor?.let { leaf ->
        BudgetEditDialog(
            leafName = leaf.parentName(groups) + leaf.name,
            initialAmount = budgets[leaf.id]?.monthlyAmountMinor ?: 0L,
            onDismiss = { editingBudgetFor = null },
            onSave = { amount ->
                vm.setBudget(leaf.id, amount)
                editingBudgetFor = null
            },
            onClear = {
                vm.clearBudget(leaf.id)
                editingBudgetFor = null
            },
        )
    }
}

private fun CategoryEntity.parentName(groups: List<ParentGroup>): String =
    groups.firstOrNull { it.parent.id == parentId }?.parent?.name?.let { "$it · " }.orEmpty()

@Composable
private fun KindTab(
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
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    )
}

@Composable
private fun ParentRow(
    group: ParentGroup,
    isExpanded: Boolean,
    budgets: Map<Long, com.householdbudget.app.data.local.entity.CategoryBudgetEntity>,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddChild: () -> Unit,
    onRenameChild: (CategoryEntity) -> Unit,
    onDeleteChild: (CategoryEntity) -> Unit,
    onEditBudget: (CategoryEntity) -> Unit,
    onMoveChildUp: (CategoryEntity) -> Unit,
    onMoveChildDown: (CategoryEntity) -> Unit,
    onMoveParentUp: () -> Unit,
    onMoveParentDown: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = group.parent.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${group.children.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onMoveParentUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(onClick = onMoveParentDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.category_rename))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.category_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(modifier = Modifier.padding(start = 24.dp, end = 8.dp)) {
                    group.children.forEach { leaf ->
                        val budget = budgets[leaf.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = leaf.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (budget != null) {
                                    Text(
                                        text = stringResource(
                                            R.string.budget_badge,
                                            com.householdbudget.app.ui.util.formatWon(budget.monthlyAmountMinor),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            IconButton(onClick = { onEditBudget(leaf) }) {
                                Icon(
                                    imageVector = Icons.Filled.AccountBalanceWallet,
                                    contentDescription = stringResource(R.string.budget_set),
                                    tint = if (budget != null) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onMoveChildUp(leaf) }) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                            }
                            IconButton(onClick = { onMoveChildDown(leaf) }) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                            }
                            IconButton(onClick = { onRenameChild(leaf) }) {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                            IconButton(onClick = { onDeleteChild(leaf) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAddChild)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp).size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.category_add_child),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NamePromptDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.category_name_label)) },
                placeholder = { Text(stringResource(R.string.category_name_hint)) },
                singleLine = true,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.category_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.category_cancel))
            }
        },
    )
}

@Composable
private fun validationTitle(err: CategoryValidationError): String =
    when (err) {
        CategoryValidationError.DuplicateName -> stringResource(R.string.category_duplicate_name)
        CategoryValidationError.KindMismatch -> stringResource(R.string.category_kind_mismatch)
        CategoryValidationError.EmptyName -> stringResource(R.string.category_empty_name)
        CategoryValidationError.NotFound -> stringResource(R.string.archive_missing)
        CategoryValidationError.LastParentOfKind ->
            stringResource(R.string.category_last_parent_of_kind)
    }

@Composable
private fun BudgetEditDialog(
    leafName: String,
    initialAmount: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember {
        mutableStateOf(if (initialAmount > 0) initialAmount.toString() else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$leafName · ${stringResource(R.string.budget_set)}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    if (v.isEmpty() || v.all { it.isDigit() }) text = v
                },
                label = { Text(stringResource(R.string.budget_monthly_amount)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = text.toLongOrNull() ?: 0L
                    if (amount > 0) onSave(amount)
                },
                enabled = (text.toLongOrNull() ?: 0L) > 0L,
            ) {
                Text(stringResource(R.string.category_save))
            }
        },
        dismissButton = {
            Row {
                if (initialAmount > 0) {
                    TextButton(onClick = onClear) {
                        Text(
                            stringResource(R.string.budget_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.category_cancel))
                }
            }
        },
    )
}
