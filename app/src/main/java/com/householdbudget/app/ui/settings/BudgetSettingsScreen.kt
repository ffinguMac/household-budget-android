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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.cardBorder
import com.householdbudget.app.ui.components.pushedBottomPadding
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.categoryStyleOf
import com.householdbudget.app.ui.util.formatDigitsGrouped
import com.householdbudget.app.ui.util.formatWon
import com.householdbudget.app.ui.util.stripDigits
import kotlinx.coroutines.launch

/** 금액 입력 시트가 어느 항목을 편집 중인지. */
private sealed interface BudgetSheetTarget {
    data object Monthly : BudgetSheetTarget

    data class Category(val id: Long, val name: String) : BudgetSheetTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSettingsScreen(
    repository: BudgetRepository,
    budgetViewModel: BudgetViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        (context.applicationContext as BudgetApplication).container.userPreferencesRepository
    }
    val scope = rememberCoroutineScope()

    val monthlyBudgetMinor by prefs.monthlyBudgetMinor.collectAsStateWithLifecycle(initialValue = null)
    val categoryBudgetRows by remember(repository) { repository.observeCategoryBudgets() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val parentsByKind by budgetViewModel.parentsByKind.collectAsStateWithLifecycle()
    val transactions by budgetViewModel.transactions.collectAsStateWithLifecycle()

    val budgetByCategory = remember(categoryBudgetRows) {
        categoryBudgetRows.filter { it.enabled }.associate { it.categoryId to it.monthlyAmountMinor }
    }
    val expenseParents = parentsByKind[CategoryKind.EXPENSE].orEmpty()
    val spentByParent = remember(transactions) {
        transactions
            .filter { it.kind == CategoryKind.EXPENSE.storage }
            .groupBy { it.parentCategoryId }
            .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
    }

    var sheetTarget by remember { mutableStateOf<BudgetSheetTarget?>(null) }
    var amountText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    fun openSheet(target: BudgetSheetTarget, currentMinor: Long?) {
        amountText = currentMinor?.toString().orEmpty()
        sheetTarget = target
    }

    fun applySheet() {
        val target = sheetTarget ?: return
        val value = amountText.toLongOrNull()?.takeIf { it > 0 }
        when (target) {
            is BudgetSheetTarget.Monthly ->
                scope.launch { prefs.setMonthlyBudgetMinor(value) }
            is BudgetSheetTarget.Category ->
                scope.launch { repository.setCategoryBudget(target.id, value) }
        }
        sheetTarget = null
    }

    fun clearSheet() {
        val target = sheetTarget ?: return
        when (target) {
            is BudgetSheetTarget.Monthly ->
                scope.launch { prefs.setMonthlyBudgetMinor(null) }
            is BudgetSheetTarget.Category ->
                scope.launch { repository.setCategoryBudget(target.id, null) }
        }
        sheetTarget = null
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(title = stringResource(R.string.budget_settings_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = pushedBottomPadding()),
        ) {
            // ── 합계 카드 ────────────────────────────────────────────────────
            item {
                val monthly = monthlyBudgetMinor
                val assigned = expenseParents.sumOf { budgetByCategory[it.id] ?: 0L }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = cardBorder(),
                    onClick = { openSheet(BudgetSheetTarget.Monthly, monthly) },
                ) {
                    Column(modifier = Modifier.padding(Space.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.budget_settings_monthly_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (monthly != null) {
                                    Text(
                                        text = monthly.formatWon(),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.budget_settings_monthly_unset),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.budget_settings_change),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (monthly != null && assigned > 0) {
                            Spacer(Modifier.height(Space.md))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                expenseParents.forEach { parent ->
                                    val amount = budgetByCategory[parent.id] ?: 0L
                                    if (amount > 0) {
                                        Box(
                                            Modifier
                                                .weight(amount.toFloat())
                                                .fillMaxHeight()
                                                .background(categoryStyleOf(parent.name).color),
                                        )
                                    }
                                }
                                if (monthly > assigned) {
                                    Spacer(Modifier.weight((monthly - assigned).toFloat()))
                                }
                            }
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                text = if (assigned > monthly) {
                                    stringResource(R.string.budget_settings_over, (assigned - monthly).formatWon())
                                } else {
                                    stringResource(
                                        R.string.budget_settings_assigned,
                                        assigned.formatWon(),
                                        (monthly - assigned).formatWon(),
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── 카테고리별 예산 (지출 대분류) ─────────────────────────────────
            item {
                Spacer(Modifier.height(Space.xxl))
                Text(
                    text = stringResource(R.string.budget_settings_category_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
                Text(
                    text = stringResource(R.string.budget_settings_category_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
                Spacer(Modifier.height(Space.sm))
            }

            if (expenseParents.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.budget_settings_empty_categories),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = ScreenHorizontalPadding,
                            vertical = Space.md,
                        ),
                    )
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenHorizontalPadding),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = cardBorder(),
                    ) {
                        Column {
                            expenseParents.forEachIndexed { index, parent ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Space.lg),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                val budget = budgetByCategory[parent.id]
                                val spent = spentByParent[parent.id] ?: 0L
                                val style = categoryStyleOf(parent.name)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            openSheet(
                                                BudgetSheetTarget.Category(parent.id, parent.name),
                                                budget,
                                            )
                                        }
                                        .defaultMinSize(minHeight = 64.dp)
                                        .padding(horizontal = Space.lg, vertical = Space.sm),
                                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(style.color.copy(alpha = 0.16f))
                                            .clearAndSetSemantics { },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = style.emoji,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = style.color,
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = parent.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = buildAnnotatedString {
                                                append(
                                                    if (spent == 0L) {
                                                        stringResource(R.string.budget_settings_spent_none)
                                                    } else {
                                                        stringResource(R.string.budget_settings_spent, spent.formatWon())
                                                    },
                                                )
                                                if (budget != null && spent > budget) {
                                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                                        append(
                                                            stringResource(
                                                                R.string.budget_settings_spent_over,
                                                                (spent - budget).formatWon(),
                                                            ),
                                                        )
                                                    }
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (budget != null) {
                                        Text(
                                            text = budget.formatWon(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.budget_settings_set),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 금액 입력 바텀시트 ───────────────────────────────────────────────────
    val target = sheetTarget
    if (target != null) {
        ModalBottomSheet(
            onDismissRequest = { sheetTarget = null },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = ScreenHorizontalPadding)
                    .padding(bottom = Space.xxxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(
                    text = when (target) {
                        is BudgetSheetTarget.Monthly ->
                            stringResource(R.string.budget_settings_monthly_row)
                        is BudgetSheetTarget.Category -> target.name
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.budget_settings_sheet_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = formatDigitsGrouped(amountText),
                    onValueChange = { amountText = stripDigits(it) },
                    label = { Text(stringResource(R.string.edit_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { clearSheet() },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.budget_settings_none_option),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { applySheet() },
                        enabled = amountText.toLongOrNull()?.let { it > 0 } == true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.edit_save))
                    }
                }
            }
        }
    }
}
