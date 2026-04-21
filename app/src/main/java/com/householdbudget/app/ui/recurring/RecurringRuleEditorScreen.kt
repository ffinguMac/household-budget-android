package com.householdbudget.app.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader

@Composable
fun RecurringRuleEditorScreen(
    budgetViewModel: BudgetViewModel,
    repository: BudgetRepository,
    ruleId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: RecurringEditorViewModel =
        viewModel(
            factory = RecurringEditorViewModelFactory(repository, ruleId),
            key = "${ruleId ?: "new"}",
        )
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val categories by budgetViewModel.categories.collectAsStateWithLifecycle()
    var invalid by remember { mutableStateOf(false) }

    LaunchedEffect(categories, ui.isIncome, ui.categoryId, ui.loadFinished) {
        if (!ui.loadFinished || categories.isEmpty()) return@LaunchedEffect
        if (ui.categoryId == null || categories.none { it.id == ui.categoryId }) {
            val pick = categories.firstOrNull { it.isIncome == ui.isIncome } ?: categories.first()
            vm.setCategoryId(pick.id)
        }
    }

    val filtered = categories.filter { it.isIncome == ui.isIncome }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenHorizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        SectionHeader(
            title =
                if (ruleId == null) {
                    stringResource(R.string.recurring_edit_title_new)
                } else {
                    stringResource(R.string.recurring_edit_title_edit)
                },
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = ui.name,
                    onValueChange = vm::setName,
                    label = { Text(stringResource(R.string.recurring_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                )

                Text(stringResource(R.string.recurring_day), style = MaterialTheme.typography.labelLarge)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                ) {
                    gridItems((1..31).toList()) { day ->
                        FilterChip(
                            selected = ui.dayOfMonth == day,
                            onClick = { vm.setDayOfMonth(day) },
                            label = { Text(day.toString()) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.secondaryContainer,
                                ),
                        )
                    }
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ui.isIncome,
                        onClick = { vm.setIncome(true) },
                        label = { Text(stringResource(R.string.edit_income)) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    )
                    FilterChip(
                        selected = !ui.isIncome,
                        onClick = { vm.setIncome(false) },
                        label = { Text(stringResource(R.string.edit_expense)) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    )
                }

                Text(stringResource(R.string.edit_category), style = MaterialTheme.typography.labelLarge)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                ) {
                    gridItems(filtered, key = { it.id }) { c ->
                        FilterChip(
                            selected = ui.categoryId == c.id,
                            onClick = { vm.setCategoryId(c.id) },
                            label = { Text(c.name) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.secondaryContainer,
                                ),
                        )
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.recurring_enabled),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = ui.enabled,
                        onCheckedChange = vm::setEnabled,
                        colors =
                            SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    )
                }
            }
        }

        if (invalid) {
            Text(
                stringResource(R.string.recurring_invalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = {
                vm.save(
                    onDone = onSaved,
                    onInvalid = { invalid = true },
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

        if (ruleId != null) {
            TextButton(
                onClick = { vm.delete(onDone = onSaved) },
                enabled = !ui.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.recurring_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
