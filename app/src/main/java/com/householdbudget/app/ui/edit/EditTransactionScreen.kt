@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.householdbudget.app.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.kindAccent
import com.householdbudget.app.ui.theme.kindContainer
import com.householdbudget.app.ui.theme.kindOnContainer
import com.householdbudget.app.ui.theme.kindSignPrefix
import com.householdbudget.app.ui.util.formatDayLabel
import com.householdbudget.app.ui.util.formatDigitsGrouped
import com.householdbudget.app.ui.util.formatWon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 소분류가 이 개수를 넘으면 칩 대신 바텀시트 피커를 쓴다. */
private const val LEAF_SHEET_THRESHOLD = 12

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
    val kbankCardEnabled by budgetViewModel.kbankCardEnabled.collectAsStateWithLifecycle()
    val recentIdsByKind by vm.recentCategoryIdsByKind.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.of("Asia/Seoul") }
    val today = remember { LocalDate.now(ZoneId.of("Asia/Seoul")) }
    var cashbackChannel by remember { mutableStateOf(CashbackChannel.OFFLINE) }
    var applyCashback by remember { mutableStateOf(true) }
    val showCashbackSection = ui.kind == CategoryKind.EXPENSE && kbankCardEnabled && transactionId == null

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
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    // 화면이 열리면 바로 금액을 입력할 수 있게 내장 키패드를 기본 표시한다.
    var keypadVisible by rememberSaveable { mutableStateOf(true) }

    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current

    val requestClose: () -> Unit = {
        if (vm.hasUnsavedChanges()) showDiscardConfirm = true else onClose()
    }
    BackHandler { requestClose() }

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
                    IconButton(onClick = requestClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (transactionId != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !ui.isSaving,
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.edit_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = ScreenHorizontalPadding, vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    if (!ui.canSave) {
                        Text(
                            text =
                                stringResource(
                                    if (ui.amountMinor <= 0L) R.string.edit_hint_enter_amount
                                    else R.string.edit_hint_pick_category,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (keypadVisible) {
                        AmountKeypad(
                            onDigits = vm::appendAmountDigits,
                            onDelete = vm::deleteLastAmountDigit,
                            onClear = vm::clearAmount,
                        )
                    }
                    Button(
                        onClick = {
                            vm.save(
                                cashbackChannel =
                                    if (showCashbackSection && applyCashback) cashbackChannel else null,
                                onSuccess = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onClose()
                                },
                                onInvalid = { /* 버튼이 유효할 때만 활성화되므로 도달하지 않는다. */ },
                            )
                        },
                        enabled = ui.canSave && !ui.isSaving,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    ) {
                        if (ui.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                stringResource(R.string.edit_save),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // ── 카드 1: 금액 + 종류 + 날짜 (얼마 → 언제) ──
            EditCard {
                // 큰 금액 디스플레이. 탭하면 내장 키패드를 다시 연다.
                val amountEmpty = ui.amountText.isEmpty()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            focusManager.clearFocus()
                            keypadVisible = true
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text =
                            if (amountEmpty) {
                                stringResource(R.string.edit_amount_zero)
                            } else {
                                stringResource(
                                    R.string.edit_amount_display,
                                    kindSignPrefix(ui.kind),
                                    formatDigitsGrouped(ui.amountText),
                                )
                            },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (amountEmpty) MaterialTheme.colorScheme.onSurfaceVariant
                            else kindAccent(ui.kind),
                    )
                }

                KindSelector(selected = ui.kind, onSelect = vm::setKind)

                // 날짜: 현재 라벨 + 빠른 선택 칩.
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Text(
                            text = ui.date.formatDayLabel(today),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (ui.date.isAfter(today)) {
                            Text(
                                text = stringResource(R.string.edit_date_future_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        DateQuickChip(
                            selected = ui.date == today,
                            label = stringResource(R.string.edit_date_today),
                            onClick = { vm.setDate(today) },
                        )
                        DateQuickChip(
                            selected = ui.date == today.minusDays(1),
                            label = stringResource(R.string.edit_date_yesterday),
                            onClick = { vm.setDate(today.minusDays(1)) },
                        )
                        DateQuickChip(
                            selected = ui.date != today && ui.date != today.minusDays(1),
                            label = stringResource(R.string.edit_pick_date),
                            onClick = { showDatePicker = true },
                        )
                    }
                }
            }

            // ── 카드 2: 카테고리 ──
            EditCard {
                Text(
                    text = stringResource(R.string.edit_category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (recentLeaves.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.edit_recent_categories),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        recentLeaves.forEach { leaf ->
                            val pid = leaf.parentId ?: return@forEach
                            FilterChip(
                                selected = ui.categoryId == leaf.id,
                                onClick = { vm.setParent(pid, leaf) },
                                label = { Text(leaf.name) },
                                colors = leafChipColors(),
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.edit_category_parent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    parents.forEach { parent ->
                        FilterChip(
                            selected = ui.parentId == parent.id,
                            onClick = {
                                val first = childrenByParent[parent.id]?.firstOrNull()
                                vm.setParent(parent.id, first)
                            },
                            label = { Text(parent.name) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.edit_category_child),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    ui.parentId == null || leaves.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.edit_category_pick_parent_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    leaves.size > LEAF_SHEET_THRESHOLD -> {
                        Surface(
                            onClick = { showCategorySheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = Space.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text =
                                        leaves.firstOrNull { it.id == ui.categoryId }?.name
                                            ?: stringResource(R.string.edit_hint_pick_category),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.edit_category_open_picker),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    else -> {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            leaves.forEach { leaf ->
                                FilterChip(
                                    selected = ui.categoryId == leaf.id,
                                    onClick = { vm.setCategoryId(leaf.id) },
                                    label = { Text(leaf.name) },
                                    colors = leafChipColors(),
                                )
                            }
                        }
                    }
                }
                if (ui.loadFinished && ui.categoryId == null) {
                    Text(
                        text = stringResource(R.string.edit_hint_pick_category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ── 카드 3: 메모 ──
            EditCard {
                OutlinedTextField(
                    value = ui.memo,
                    onValueChange = vm::setMemo,
                    label = { Text(stringResource(R.string.edit_memo)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) keypadVisible = false },
                    shape = MaterialTheme.shapes.medium,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                )
            }

            // ── 카드 4: 케이뱅크 캐시백 (접히는 섹션) ──
            if (showCashbackSection) {
                EditCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.edit_cashback_apply),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = applyCashback,
                            onCheckedChange = { applyCashback = it },
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                        )
                    }
                    AnimatedVisibility(visible = applyCashback) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                            Text(
                                text = stringResource(R.string.edit_cashback_channel),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                FilterChip(
                                    selected = cashbackChannel == CashbackChannel.OFFLINE,
                                    onClick = { cashbackChannel = CashbackChannel.OFFLINE },
                                    label = { Text(stringResource(R.string.edit_cashback_offline)) },
                                    colors = leafChipColors(),
                                )
                                FilterChip(
                                    selected = cashbackChannel == CashbackChannel.ONLINE,
                                    onClick = { cashbackChannel = CashbackChannel.ONLINE },
                                    label = { Text(stringResource(R.string.edit_cashback_online)) },
                                    colors = leafChipColors(),
                                )
                            }
                            val rate = if (cashbackChannel == CashbackChannel.ONLINE) 11L else 6L
                            val previewAmount = ui.amountMinor * rate / 1000L
                            if (previewAmount > 0L) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.edit_cashback_preview,
                                            previewAmount.formatWon(),
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.sm))
        }
    }

    if (showCategorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var query by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(
                    text = stringResource(R.string.edit_category_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.edit_category_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                )
                val trimmedQuery = query.trim()
                val sections =
                    parents.mapNotNull { parent ->
                        val children = childrenByParent[parent.id].orEmpty()
                        val filtered =
                            when {
                                trimmedQuery.isEmpty() -> children
                                parent.name.contains(trimmedQuery) -> children
                                else -> children.filter { it.name.contains(trimmedQuery) }
                            }
                        if (filtered.isEmpty()) null else parent to filtered
                    }
                if (sections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.edit_category_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.xl),
                    )
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp),
                    ) {
                        sections.forEach { (parent, children) ->
                            item(key = "parent-${parent.id}") {
                                Text(
                                    text = parent.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.md, bottom = Space.xs),
                                )
                            }
                            items(children, key = { "leaf-${it.id}" }) { leaf ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable {
                                            vm.setParent(parent.id, leaf)
                                            showCategorySheet = false
                                        }
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = Space.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = leaf.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (ui.categoryId == leaf.id) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.lg))
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

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            shape = MaterialTheme.shapes.extraLarge,
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

/** 화면 공통 카드 컨테이너. */
@Composable
private fun EditCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier =
            modifier
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
        Column(
            Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            content = content,
        )
    }
}

/** 수입/지출/저축 3분할 세그먼트. 선택 색은 종류별 컨테이너 색을 쓴다. */
@Composable
private fun KindSelector(
    selected: CategoryKind,
    onSelect: (CategoryKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries =
        listOf(
            CategoryKind.INCOME to stringResource(R.string.edit_income),
            CategoryKind.EXPENSE to stringResource(R.string.edit_expense),
            CategoryKind.SAVINGS to stringResource(R.string.edit_savings),
        )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        entries.forEach { (kind, label) ->
            val isSelected = kind == selected
            Surface(
                onClick = { onSelect(kind) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color =
                    if (isSelected) kindContainer(kind)
                    else MaterialTheme.colorScheme.surface,
                contentColor =
                    if (isSelected) kindOnContainer(kind)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                border =
                    BorderStroke(
                        width = 1.dp,
                        color =
                            if (isSelected) kindAccent(kind)
                            else MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateQuickChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = leafChipColors(),
    )
}

@Composable
private fun leafChipColors() =
    FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

/** 내장 숫자 키패드 (1~9 / 00 / 0 / ⌫). 시스템 키보드 대신 쓴다. */
@Composable
private fun AmountKeypad(
    onDigits: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        KeypadRows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                row.forEach { key ->
                    if (key == KEY_DELETE) {
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .combinedClickable(
                                    onClick = onDelete,
                                    onLongClick = onClear,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = stringResource(R.string.edit_keypad_backspace),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        Surface(
                            onClick = { onDigits(key) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
