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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
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
import com.householdbudget.app.ui.util.formatWon

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
    var editingIconFor by remember { mutableStateOf<CategoryEntity?>(null) }

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
            // ── 헤더 ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(
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

            // ── 종류 탭 ──────────────────────────────────────────────────────
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
                            onEditIcon = { cat -> editingIconFor = cat },
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

    // ── 다이얼로그 ────────────────────────────────────────────────────────────
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

    editingIconFor?.let { target ->
        IconPickerDialog(
            categoryName = target.name,
            currentIcon = target.icon,
            onDismiss = { editingIconFor = null },
            onPick = { icon ->
                vm.setIcon(target.id, icon)
                editingIconFor = null
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
private fun KindTab(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

// ── ParentRow ─────────────────────────────────────────────────────────────────

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
    onEditIcon: (CategoryEntity) -> Unit,
    onMoveChildUp: (CategoryEntity) -> Unit,
    onMoveChildDown: (CategoryEntity) -> Unit,
    onMoveParentUp: () -> Unit,
    onMoveParentDown: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column {
            // ── 헤더 행 ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryAvatar(
                    icon = group.parent.icon,
                    fallbackText = group.parent.name.take(1),
                    size = 44.dp,
                    onClick = { onEditIcon(group.parent) },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.parent.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "소분류 ${group.children.size}개",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.category_rename)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("순서 올리기") },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                            onClick = { menuExpanded = false; onMoveParentUp() },
                        )
                        DropdownMenuItem(
                            text = { Text("순서 내리기") },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                            onClick = { menuExpanded = false; onMoveParentDown() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.category_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }

            // ── 펼쳐진 소분류 목록 ────────────────────────────────────────────
            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    group.children.forEach { leaf ->
                        ChildRow(
                            leaf = leaf,
                            budget = budgets[leaf.id],
                            onEditIcon = onEditIcon,
                            onEditBudget = onEditBudget,
                            onRenameChild = onRenameChild,
                            onDeleteChild = onDeleteChild,
                            onMoveChildUp = onMoveChildUp,
                            onMoveChildDown = onMoveChildDown,
                        )
                    }
                    // 소분류 추가 버튼
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onAddChild)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.category_add_child),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

// ── ChildRow ──────────────────────────────────────────────────────────────────

@Composable
private fun ChildRow(
    leaf: CategoryEntity,
    budget: com.householdbudget.app.data.local.entity.CategoryBudgetEntity?,
    onEditIcon: (CategoryEntity) -> Unit,
    onEditBudget: (CategoryEntity) -> Unit,
    onRenameChild: (CategoryEntity) -> Unit,
    onDeleteChild: (CategoryEntity) -> Unit,
    onMoveChildUp: (CategoryEntity) -> Unit,
    onMoveChildDown: (CategoryEntity) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryAvatar(
            icon = leaf.icon,
            fallbackText = leaf.name.take(1),
            size = 36.dp,
            onClick = { onEditIcon(leaf) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = leaf.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (budget != null) {
                Text(
                    text = stringResource(R.string.budget_badge, budget.monthlyAmountMinor.formatWon()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.budget_set)) },
                    leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, null) },
                    onClick = { menuExpanded = false; onEditBudget(leaf) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.category_rename)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menuExpanded = false; onRenameChild(leaf) },
                )
                DropdownMenuItem(
                    text = { Text("순서 올리기") },
                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                    onClick = { menuExpanded = false; onMoveChildUp(leaf) },
                )
                DropdownMenuItem(
                    text = { Text("순서 내리기") },
                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                    onClick = { menuExpanded = false; onMoveChildDown(leaf) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.category_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { menuExpanded = false; onDeleteChild(leaf) },
                )
            }
        }
    }
}

// ── CategoryAvatar ────────────────────────────────────────────────────────────

@Composable
private fun CategoryAvatar(
    icon: String?,
    fallbackText: String,
    size: Dp,
    onClick: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it },
            contentAlignment = Alignment.Center,
        ) {
            if (!icon.isNullOrBlank()) {
                Text(text = icon, style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    text = fallbackText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 탭 가능함을 알리는 편집 뱃지
        if (onClick != null) {
            Box(
                modifier = Modifier
                    .size((size.value * 0.38f).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size((size.value * 0.2f).dp),
                )
            }
        }
    }
}

// ── 다이얼로그들 ───────────────────────────────────────────────────────────────

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
                colors = OutlinedTextFieldDefaults.colors(
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.category_cancel)) }
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
        CategoryValidationError.LastParentOfKind -> stringResource(R.string.category_last_parent_of_kind)
    }

private val EMOJI_PALETTE = listOf(
    "🍴", "🍚", "☕", "🍪", "🍺", "🍰", "🍜", "🍔",
    "🚌", "🚗", "🚇", "✈️", "⛽", "🅿️",
    "🏠", "💡", "💧", "🔌", "📱", "💻",
    "🛒", "👕", "👟", "💄", "🎁",
    "🎬", "🎮", "🎵", "📚", "🏋️",
    "💊", "🏥", "🦷",
    "💰", "💵", "💳", "💸",
    "🏦", "📈", "📊", "🛡️", "🎯",
    "🐶", "🎓", "🧸", "📌",
)

@Composable
private fun IconPickerDialog(
    categoryName: String,
    currentIcon: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    var custom by remember { mutableStateOf(currentIcon.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$categoryName · ${stringResource(R.string.icon_pick_title)}") },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    gridItems(EMOJI_PALETTE) { emoji ->
                        val selected = emoji == currentIcon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable { onPick(emoji) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { if (it.length <= 4) custom = it },
                    label = { Text(stringResource(R.string.icon_custom)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row {
                if (!currentIcon.isNullOrBlank()) {
                    TextButton(onClick = { onPick(null) }) {
                        Text(stringResource(R.string.icon_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    onClick = { onPick(custom.trim().ifEmpty { null }) },
                ) { Text(stringResource(R.string.category_save)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.category_cancel)) }
        },
    )
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
                onValueChange = { v -> if (v.isEmpty() || v.all { it.isDigit() }) text = v },
                label = { Text(stringResource(R.string.budget_monthly_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
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
            ) { Text(stringResource(R.string.category_save)) }
        },
        dismissButton = {
            Row {
                if (initialAmount > 0) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.budget_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.category_cancel)) }
            }
        },
    )
}
