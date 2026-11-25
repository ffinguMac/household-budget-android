@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.householdbudget.app.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.key
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.EditTransactionViewModel
import com.householdbudget.app.ui.EditTransactionViewModelFactory
import com.householdbudget.app.ui.autoCategorySelection
import com.householdbudget.app.ui.components.CategoryChip
import com.householdbudget.app.ui.components.CategoryPicker
import com.householdbudget.app.ui.components.CategorySearchSheet
import com.householdbudget.app.ui.components.CustomCategoryDialogs
import com.householdbudget.app.ui.components.CustomCategoryRequest
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatDigitsGrouped
import com.householdbudget.app.ui.util.formatShortDayLabel
import com.householdbudget.app.ui.util.formatWon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private const val KEY_DOUBLE_ZERO = "00"
private const val KEY_DELETE = "⌫"
private val KeypadRows =
    listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(KEY_DOUBLE_ZERO, "0", KEY_DELETE),
    )

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
    val hiddenLeafIds by budgetViewModel.hiddenCustomLeafIds.collectAsStateWithLifecycle()
    val recentIdsByKind by vm.recentCategoryIdsByKind.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now(ZoneId.of("Asia/Seoul")) }

    val parents = parentsByKind[ui.kind].orEmpty()
    val leaves = ui.parentId?.let { childrenByParent[it] }.orEmpty()
    val leavesById =
        remember(childrenByParent) {
            childrenByParent.values.flatten().associateBy { it.id }
        }
    val recentLeaves =
        recentIdsByKind[ui.kind].orEmpty()
            .mapNotNull { id -> leavesById[id]?.takeIf { it.parentId != null } }

    // 최초 로드 또는 kind 변경 시 대분류/소분류 자동 선택.
    LaunchedEffect(parents, childrenByParent, ui.parentId, ui.categoryId, ui.loadFinished) {
        if (!ui.loadFinished) return@LaunchedEffect
        autoCategorySelection(parents, childrenByParent, ui.parentId, ui.categoryId)?.let { (p, leafId) ->
            vm.setParent(p, childrenByParent[p]?.firstOrNull { it.id == leafId })
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var customRequest by remember { mutableStateOf<CustomCategoryRequest?>(null) }
    // 메모에 포커스가 있으면 시스템 키보드를 쓰므로 내장 키패드를 숨긴다.
    var memoFocused by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    // 시스템 뒤로 가기로 키보드만 닫혀도 메모 포커스를 풀어 키패드를 되살린다.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (!imeVisible) focusManager.clearFocus() }

    val requestClose: () -> Unit = {
        if (vm.hasUnsavedChanges()) showDiscardConfirm = true else onClose()
    }
    BackHandler { requestClose() }

    // 하단 + 버튼에서 시트처럼 올라오는 화면 — 최상단 코너만 라운드.
    val sheetShape =
        MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        )

    BoxWithConstraints {
        val keyRowHeight = if (maxHeight < 700.dp) 44.dp else 48.dp
        Scaffold(
            modifier = Modifier.clip(sheetShape),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = Space.sm),
                ) {
                    // 드래그 핸들 모양의 장식 바.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = Space.sm),
                    ) {
                        if (transactionId != null) {
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                enabled = !ui.isSaving,
                                modifier = Modifier.align(Alignment.CenterStart),
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.edit_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        KindSelector(
                            selected = ui.kind,
                            onSelect = vm::setKind,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        IconButton(onClick = requestClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // 금액은 스크롤 밖에 고정한다. 누르면 메모 포커스를 풀고 키패드를 다시 연다.
                    val amountEmpty = ui.amountText.isEmpty()
                    Text(
                        text =
                            if (amountEmpty) {
                                stringResource(R.string.edit_amount_zero)
                            } else {
                                stringResource(R.string.edit_amount_display, formatDigitsGrouped(ui.amountText))
                            },
                        style = MaterialTheme.typography.displaySmall,
                        color =
                            if (amountEmpty) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Space.md)
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                ) { focusManager.clearFocus() },
                    )
                    Box(Modifier.fillMaxWidth().padding(bottom = Space.sm), contentAlignment = Alignment.Center) {
                        // 메뉴가 알약 아래에 붙도록 알약 크기의 Box 에 둔다.
                        Box {
                            DatePill(
                                label = datePillLabel(ui.date, today),
                                onClick = { showDateMenu = true },
                            )
                            DropdownMenu(
                                expanded = showDateMenu,
                                onDismissRequest = { showDateMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_date_today)) },
                                    onClick = {
                                        vm.setDate(today)
                                        showDateMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_date_yesterday)) },
                                    onClick = {
                                        vm.setDate(today.minusDays(1))
                                        showDateMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_pick_date)) },
                                    onClick = {
                                        showDateMenu = false
                                        showDatePicker = true
                                    },
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        if (!memoFocused) {
                            AmountKeypad(
                                rowHeight = keyRowHeight,
                                onDigits = vm::appendAmountDigits,
                                onDelete = vm::deleteLastAmountDigit,
                                onClear = vm::clearAmount,
                            )
                        }
                        SaveButton(
                            label =
                                when {
                                    ui.amountMinor <= 0L -> stringResource(R.string.edit_save_need_amount)
                                    ui.categoryId == null -> stringResource(R.string.edit_save_need_category)
                                    else -> stringResource(R.string.edit_save_amount, ui.amountMinor.formatWon())
                                },
                            enabled = ui.canSave && !ui.isSaving,
                            saving = ui.isSaving,
                            onClick = {
                                vm.save(
                                    onSuccess = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onClose()
                                    },
                                    onInvalid = { /* 버튼이 유효할 때만 활성화되므로 도달하지 않는다. */ },
                                )
                            },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenHorizontalPadding, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                // 카테고리 한 줄 상태 — 선택 결과의 유일한 표시.
                val selectedParent = parents.firstOrNull { it.id == ui.parentId }
                val selectedLeaf = leaves.firstOrNull { it.id == ui.categoryId }
                Row(
                    Modifier.heightIn(min = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    if (selectedParent != null && selectedLeaf != null) {
                        Text(
                            text = stringResource(R.string.edit_category_selected, selectedParent.name, selectedLeaf.name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.edit_category),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (recentLeaves.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.edit_recent_categories),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(recentLeaves, key = { it.id }) { leaf ->
                            CategoryChip(
                                label = leaf.name,
                                selected = false,
                                onClick = { vm.setParent(leaf.parentId!!, leaf) },
                            )
                        }
                    }
                }

                // 종류를 바꾸면 펼쳐 둔 대분류를 접는다.
                key(ui.kind) {
                    CategoryPicker(
                        parents = parents,
                        childrenByParent = childrenByParent,
                        selectedLeafId = ui.categoryId,
                        onLeafSelected = { leaf ->
                            // 하드웨어 키보드는 IME 가 안 떠서 메모 포커스가 남아 키패드가 숨은 채로 남는다.
                            focusManager.clearFocus()
                            vm.setParent(leaf.parentId!!, leaf)
                        },
                        onMore = { showCategorySheet = true },
                        hiddenLeafIds = hiddenLeafIds,
                        onAddCustom = { customRequest = CustomCategoryRequest.Add(it) },
                        onEditCustom = { customRequest = CustomCategoryRequest.Edit(it) },
                    )
                }

                BasicTextField(
                    value = ui.memo,
                    onValueChange = vm::setMemo,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier =
                        Modifier
                            .padding(top = Space.sm)
                            .fillMaxWidth()
                            .onFocusChanged { memoFocused = it.isFocused },
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = Space.lg),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (ui.memo.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.edit_memo_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
    }

    if (showCategorySheet) {
        CategorySearchSheet(
            parents = parents,
            childrenByParent = childrenByParent,
            selectedLeafId = ui.categoryId,
            onLeafSelected = { leaf ->
                vm.setParent(leaf.parentId!!, leaf)
                showCategorySheet = false
            },
            onDismiss = { showCategorySheet = false },
            onEditCustom = { customRequest = CustomCategoryRequest.Edit(it) },
        )
    }

    CustomCategoryDialogs(
        request = customRequest,
        budgetViewModel = budgetViewModel,
        onDismiss = { customRequest = null },
        onCreated = { vm.setParent(it.parentId!!, it) },
        // 고르던 항목을 지우면 옮겨 간 곳으로 바꿔 둬야 저장이 "Category not found"로 죽지 않는다.
        onDeleted = { deleted, movedTo -> if (ui.categoryId == deleted.id) vm.setParent(movedTo.parentId!!, movedTo) },
    )

    if (showDatePicker) {
        val millis = ui.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() // DatePicker 는 UTC 기준
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = state.selectedDateMillis
                        if (selected != null) {
                            val picked =
                                Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                            vm.setDate(picked)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.edit_confirm_ok))
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.edit_delete_confirm_title)) },
            text = { Text(stringResource(R.string.edit_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.delete(onSuccess = onClose)
                    },
                ) {
                    Text(stringResource(R.string.edit_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.edit_dismiss))
                }
            },
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.edit_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onClose()
                    },
                ) {
                    Text(
                        stringResource(R.string.edit_discard_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.edit_discard_dismiss))
                }
            },
        )
    }
}

/** "오늘 · 11.22 (일)". 오늘·어제·미래가 아니면 날짜만. */
@Composable
private fun datePillLabel(date: LocalDate, today: LocalDate): String {
    val short = date.formatShortDayLabel()
    val prefix =
        when {
            date == today -> stringResource(R.string.edit_date_today)
            date == today.minusDays(1) -> stringResource(R.string.edit_date_yesterday)
            date.isAfter(today) -> stringResource(R.string.edit_date_future_badge)
            else -> return short
        }
    return stringResource(R.string.edit_date_pill, prefix, short)
}

@Composable
private fun DatePill(label: String, onClick: () -> Unit) {
    // 보이는 알약은 32dp, 터치 영역은 48dp.
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = Space.md, end = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 지출/수입/저축 3칸 세그먼트. 트랙 `surfaceVariant`, 선택 칸 `surface`. */
@Composable
private fun KindSelector(
    selected: CategoryKind,
    onSelect: (CategoryKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries =
        listOf(
            CategoryKind.EXPENSE to stringResource(R.string.edit_expense),
            CategoryKind.INCOME to stringResource(R.string.edit_income),
            CategoryKind.SAVINGS to stringResource(R.string.edit_savings),
        )
    // 보이는 트랙은 36dp, 각 칸의 터치 영역은 48dp.
    Box(modifier.width(240.dp).height(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            entries.forEach { (kind, label) ->
                val isSelected = kind == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(kind) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .padding(horizontal = Space.xs)
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color =
                                if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveButton(
    label: String,
    enabled: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        shape = MaterialTheme.shapes.small,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) {
        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 내장 숫자 키패드 (1~9 / 00 / 0 / ⌫). 시스템 키보드 대신 쓴다. */
@Composable
private fun AmountKeypad(
    rowHeight: Dp,
    onDigits: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        KeypadRows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rowHeight)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .combinedClickable(
                                onClick = { if (key == KEY_DELETE) onDelete() else onDigits(key) },
                                onLongClick = if (key == KEY_DELETE) onClear else null,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == KEY_DELETE) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Backspace,
                                contentDescription = stringResource(R.string.edit_keypad_backspace),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
