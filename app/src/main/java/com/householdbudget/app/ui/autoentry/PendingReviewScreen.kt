package com.householdbudget.app.ui.autoentry

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.R
import com.householdbudget.app.data.local.entity.AutoEntryEntity
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.pushedBottomPadding
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.CategoryDot
import com.householdbudget.app.ui.theme.categoryStyleOf
import com.householdbudget.app.ui.theme.categoryLabel
import com.householdbudget.app.ui.util.formatShortDayLabel
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import kotlinx.coroutines.launch

fun defaultKindFor(direction: String): CategoryKind =
    if (direction == "IN" || direction == "REFUND") CategoryKind.INCOME else CategoryKind.EXPENSE

@StringRes
fun reasonLabelRes(reason: String?): Int =
    when (reason) {
        "DUP_CROSS_APP", "DUP_MANUAL", "DUP_REPOST" -> R.string.auto_reason_duplicate
        "UNKNOWN_DIRECTION" -> R.string.auto_reason_direction
        "RESTORED" -> R.string.auto_reason_restored
        else -> R.string.auto_reason_unclassified
    }

@Composable
fun PendingReviewScreen(
    budgetViewModel: BudgetViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember(context) { (context.applicationContext as BudgetApplication).container.autoEntryRepository }
    val entries by remember(repo) { repo.observePending() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by remember { mutableStateOf<AutoEntryEntity?>(null) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppTopBar(title = stringResource(R.string.auto_pending_title), onBack = onBack)
        if (entries.isEmpty()) {
            EmptyState(icon = Icons.Filled.PendingActions, title = stringResource(R.string.auto_pending_empty))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = pushedBottomPadding())) {
                items(entries, key = { it.id }) { e ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { selected = e }
                            .padding(horizontal = ScreenHorizontalPadding, vertical = Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                e.merchant.ifBlank { stringResource(R.string.auto_pending_no_name) },
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(e.amountMinor.formatWon(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            LocalDate.ofEpochDay(e.occurredEpochDay).formatShortDayLabel() + " · " +
                                stringResource(reasonLabelRes(e.reason)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    selected?.let { entry ->
        ResolveSheet(budgetViewModel, entry, onDismiss = { selected = null }) { leafId ->
            selected = null
            scope.launch { repo.resolvePending(entry.id, leafId) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ResolveSheet(
    budgetViewModel: BudgetViewModel,
    entry: AutoEntryEntity,
    onDismiss: () -> Unit,
    onResolve: (Long?) -> Unit,
) {
    val parentsByKind by budgetViewModel.parentsByKind.collectAsStateWithLifecycle()
    val childrenByParent by budgetViewModel.childrenByParent.collectAsStateWithLifecycle()
    var kind by remember(entry.id) { mutableStateOf(defaultKindFor(entry.direction)) }
    val kindLabels = listOf(
        CategoryKind.EXPENSE to R.string.summary_expense,
        CategoryKind.INCOME to R.string.summary_income,
        CategoryKind.SAVINGS to R.string.summary_savings,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding).padding(bottom = Space.xxxl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                kindLabels.forEach { (k, res) ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(res)) })
                }
            }
            parentsByKind[kind].orEmpty().forEach { parent ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CategoryDot(categoryStyleOf(parent.name).color)
                    Text(categoryLabel(parent.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    childrenByParent[parent.id].orEmpty().forEach { leaf ->
                        FilterChip(selected = false, onClick = { onResolve(leaf.id) }, label = { Text(leaf.name) })
                    }
                }
            }
            OutlinedButton(onClick = { onResolve(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auto_skip))
            }
        }
    }
}
