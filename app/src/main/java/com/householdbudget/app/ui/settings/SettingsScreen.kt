package com.householdbudget.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.householdbudget.app.R
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    budgetViewModel: BudgetViewModel,
    onOpenRecurringRules: () -> Unit,
    onOpenCategoryManagement: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val payday by budgetViewModel.paydayDom.collectAsStateWithLifecycle()
    val kbankCardEnabled by budgetViewModel.kbankCardEnabled.collectAsStateWithLifecycle()
    var showSavedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(showSavedFeedback) {
        if (showSavedFeedback) {
            delay(1600)
            showSavedFeedback = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── 에디토리얼 헤더 ──────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "PREFERENCES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                        2.0f, androidx.compose.ui.unit.TextUnitType.Sp,
                    ),
                )
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // ── 급여일 섹션 ──────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_payday_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_payday_select),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 0.dp,
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_payday_current, payday),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                        ) {
                            items((1..31).toList()) { day ->
                                FilterChip(
                                    selected = payday == day,
                                    onClick = {
                                        budgetViewModel.setPaydayDom(day)
                                        showSavedFeedback = true
                                    },
                                    label = { Text(text = day.toString()) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                            }
                        }

                        if (showSavedFeedback) {
                            Text(
                                text = stringResource(R.string.settings_saved),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        // ── K-Bank 캐시백 섹션 ───────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_kbank_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_kbank_card_toggle),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Switch(
                                checked = kbankCardEnabled,
                                onCheckedChange = { budgetViewModel.setKbankCardEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_kbank_card_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )

                        if (kbankCardEnabled) {
                            CashbackCategoryRow(budgetViewModel = budgetViewModel)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        // ── 반복 규칙 섹션 ───────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "자동화",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenRecurringRules),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 0.dp,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Autorenew,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_open_recurring),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "정기 수입·지출 자동 등록 관리",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        // ── 카테고리 관리 섹션 ───────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "카테고리",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenCategoryManagement),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 0.dp,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_open_categories),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_open_categories_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        // ── 통계 섹션 ────────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenStats),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            tonalElevation = 0.dp,
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.InsertChart,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_open_stats),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_open_stats_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CashbackCategoryRow(
    budgetViewModel: BudgetViewModel,
) {
    val categories by budgetViewModel.categories.collectAsStateWithLifecycle()
    val parentsByKind by budgetViewModel.parentsByKind.collectAsStateWithLifecycle()
    val childrenByParent by budgetViewModel.childrenByParent.collectAsStateWithLifecycle()
    val selectedId by budgetViewModel.cashbackCategoryId.collectAsStateWithLifecycle()

    val incomeParents = parentsByKind[com.householdbudget.app.domain.CategoryKind.INCOME].orEmpty()
    val incomeLeaves = incomeParents.flatMap { p -> childrenByParent[p.id].orEmpty() }

    val selectedLeaf = incomeLeaves.firstOrNull { it.id == selectedId }
    val selectedLabel =
        selectedLeaf?.let { leaf ->
            val parentName = categories.firstOrNull { it.id == leaf.parentId }?.name
            if (parentName != null) "$parentName · ${leaf.name}" else leaf.name
        } ?: stringResource(R.string.cashback_category_auto)

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            text = stringResource(R.string.cashback_category_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = MaterialTheme.shapes.medium,
            )
            androidx.compose.material3.ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.cashback_category_auto)) },
                    onClick = {
                        budgetViewModel.setCashbackCategoryId(null)
                        expanded = false
                    },
                )
                incomeParents.forEach { parent ->
                    val leaves = childrenByParent[parent.id].orEmpty()
                    leaves.forEach { leaf ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("${parent.name} · ${leaf.name}") },
                            onClick = {
                                budgetViewModel.setCashbackCategoryId(leaf.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
