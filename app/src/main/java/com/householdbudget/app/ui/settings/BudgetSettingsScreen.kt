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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader
import com.householdbudget.app.ui.theme.Space
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
    val categoryBudgetRows by repository.observeCategoryBudgets()
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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.budget_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.recurring_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = Space.xxxl),
        ) {
            // ── 월 총 예산 ───────────────────────────────────────────────────
            item {
                SectionHeader(
                    title = stringResource(R.string.budget_settings_monthly_section),
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
                Spacer(Modifier.height(Space.sm))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenHorizontalPadding),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openSheet(BudgetSheetTarget.Monthly, monthlyBudgetMinor)
                            }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.budget_settings_monthly_row),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        val budget = monthlyBudgetMinor
                        Text(
                            text = budget?.formatWon()
                                ?: stringResource(R.string.settings_budget_subtitle_unset),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (budget != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(Space.xxl)) }

            // ── 카테고리별 예산 (지출 대분류) ─────────────────────────────────
            item {
                SectionHeader(
                    title = stringResource(R.string.budget_settings_category_section),
                    subtitle = stringResource(R.string.budget_settings_category_desc),
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
                Spacer(Modifier.height(Space.sm))
            }

            if (expenseParents.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.budget_settings_empty_categories),
                        style = MaterialTheme.typography.bodyMedium,
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
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                    ) {
                        Column {
                            expenseParents.forEachIndexed { index, parent ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Space.lg),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    )
                                }
                                val budget = budgetByCategory[parent.id]
                                val spent = spentByParent[parent.id] ?: 0L
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            openSheet(
                                                BudgetSheetTarget.Category(parent.id, parent.name),
                                                budget,
                                            )
                                        }
                                        .heightIn(min = 56.dp)
                                        .padding(horizontal = Space.lg, vertical = Space.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = parent.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.budget_settings_spent,
                                                spent.formatWon(),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = budget?.formatWon() ?: "—",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (budget != null) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Space.lg))
                Text(
                    text = stringResource(R.string.budget_settings_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
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
                    fontWeight = FontWeight.Bold,
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
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
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
                        shape = MaterialTheme.shapes.medium,
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
