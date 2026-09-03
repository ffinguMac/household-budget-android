package com.householdbudget.app.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.kindContainer
import com.householdbudget.app.ui.theme.kindOnContainer
import com.householdbudget.app.ui.util.formatDigitsGrouped
import com.householdbudget.app.ui.util.stripDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRuleEditorScreen(
    budgetViewModel: BudgetViewModel,
    repository: BudgetRepository,
    ruleId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    nonce: Int = 0,
) {
    val vm: RecurringEditorViewModel =
        viewModel(
            factory = RecurringEditorViewModelFactory(repository, ruleId),
            key = if (ruleId == null) "new_$nonce" else "$ruleId",
        )
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val parentsByKind by budgetViewModel.parentsByKind.collectAsStateWithLifecycle()
    val childrenByParent by budgetViewModel.childrenByParent.collectAsStateWithLifecycle()
    var nameError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var categoryError by remember { mutableStateOf(false) }
    var showDaySheet by remember { mutableStateOf(false) }
    val daySheetState = rememberModalBottomSheetState()

    val parents = parentsByKind[ui.kind].orEmpty()
    val leaves = ui.parentId?.let { childrenByParent[it] }.orEmpty()

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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = Space.md),
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
                Text(
                    text =
                        if (ruleId == null) stringResource(R.string.recurring_edit_title_new)
                        else stringResource(R.string.recurring_edit_title_edit),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                if (ruleId != null) {
                    IconButton(
                        onClick = { vm.delete(onDone = onSaved) },
                        enabled = !ui.isSaving,
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.recurring_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    // 좌우 균형 맞춤용 여백 (뒤로가기 버튼 폭만큼)
                    Spacer(Modifier.padding(horizontal = Space.xl))
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = Space.md),
            ) {
                Button(
                    onClick = {
                        val amountOk = (ui.amountText.toLongOrNull() ?: 0L) > 0L
                        nameError = ui.name.isBlank()
                        amountError = !amountOk
                        categoryError = ui.categoryId == null
                        if (!nameError && !amountError && !categoryError) {
                            vm.save(
                                onDone = onSaved,
                                onInvalid = {
                                    nameError = ui.name.isBlank()
                                    amountError = (ui.amountText.toLongOrNull() ?: 0L) <= 0L
                                    categoryError = ui.categoryId == null
                                },
                            )
                        }
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
            }
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    // 이름
                    OutlinedTextField(
                        value = ui.name,
                        onValueChange = {
                            vm.setName(it)
                            nameError = false
                        },
                        label = { Text(stringResource(R.string.recurring_name)) },
                        singleLine = true,
                        isError = nameError,
                        supportingText = {
                            if (nameError) {
                                Text(
                                    text = stringResource(R.string.category_empty_name),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                    )

                    // 금액 (천단위 콤마)
                    OutlinedTextField(
                        value = formatDigitsGrouped(ui.amountText),
                        onValueChange = {
                            vm.setAmountText(stripDigits(it))
                            amountError = false
                        },
                        label = { Text(stringResource(R.string.edit_amount)) },
                        singleLine = true,
                        isError = amountError,
                        supportingText = {
                            if (amountError) {
                                Text(
                                    text = stringResource(R.string.recurring_error_amount),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                    )

                    // 매월 발생일 — 바텀시트 선택
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text(
                            stringResource(R.string.recurring_day),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { showDaySheet = true },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Space.lg, vertical = Space.lg),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.recurring_line_summary, ui.dayOfMonth),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.recurring_day_pick),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.recurring_day_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 종류
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        KindChip(
                            selected = ui.kind == CategoryKind.INCOME,
                            kind = CategoryKind.INCOME,
                            label = stringResource(R.string.edit_income),
                            onClick = { vm.setKind(CategoryKind.INCOME) },
                        )
                        KindChip(
                            selected = ui.kind == CategoryKind.EXPENSE,
                            kind = CategoryKind.EXPENSE,
                            label = stringResource(R.string.edit_expense),
                            onClick = { vm.setKind(CategoryKind.EXPENSE) },
                        )
                        KindChip(
                            selected = ui.kind == CategoryKind.SAVINGS,
                            kind = CategoryKind.SAVINGS,
                            label = stringResource(R.string.edit_savings),
                            onClick = { vm.setKind(CategoryKind.SAVINGS) },
                        )
                    }

                    // 대분류
                    Text(
                        stringResource(R.string.edit_category_parent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(parents, key = { it.id }) { parent ->
                            FilterChip(
                                selected = ui.parentId == parent.id,
                                onClick = {
                                    val first = childrenByParent[parent.id]?.firstOrNull()
                                    vm.setParent(parent.id, first)
                                    categoryError = false
                                },
                                label = { Text(parent.name) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor =
                                            MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor =
                                            MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                            )
                        }
                    }

                    // 소분류
                    Text(
                        stringResource(R.string.edit_category_child),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ui.parentId == null || leaves.isEmpty()) {
                        Text(
                            stringResource(R.string.edit_category_pick_parent_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(leaves, key = { it.id }) { leaf ->
                                FilterChip(
                                    selected = ui.categoryId == leaf.id,
                                    onClick = {
                                        vm.setCategoryId(leaf.id)
                                        categoryError = false
                                    },
                                    label = { Text(leaf.name) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor =
                                                MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor =
                                                MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                )
                            }
                        }
                    }
                    if (categoryError) {
                        Text(
                            text = stringResource(R.string.recurring_error_category),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    // 메모
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

                    // 사용 여부
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.recurring_enabled),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Switch(
                            checked = ui.enabled,
                            onCheckedChange = vm::setEnabled,
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                        )
                    }
                }
            }
        }
    }

    // ── 발생일 선택 바텀시트 ─────────────────────────────────────────────────
    if (showDaySheet) {
        ModalBottomSheet(
            onDismissRequest = { showDaySheet = false },
            sheetState = daySheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding)
                    .padding(bottom = Space.xxxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(
                    text = stringResource(R.string.recurring_day_pick),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.recurring_day_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    (1..31).chunked(7).forEach { rowDays ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowDays.forEach { day ->
                                val selected = ui.dayOfMonth == day
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainer,
                                        )
                                        .clickable {
                                            vm.setDayOfMonth(day)
                                            showDaySheet = false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            repeat(7 - rowDays.size) {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindChip(
    selected: Boolean,
    kind: CategoryKind,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = kindContainer(kind),
                selectedLabelColor = kindOnContainer(kind),
            ),
    )
}
