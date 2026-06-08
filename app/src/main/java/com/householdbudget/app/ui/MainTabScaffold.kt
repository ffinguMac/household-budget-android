package com.householdbudget.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.ui.archive.ArchiveDetailScreen
import com.householdbudget.app.ui.archive.ArchiveListScreen
import com.householdbudget.app.ui.calendar.CalendarScreen
import com.householdbudget.app.ui.calendar.CalendarViewModel
import com.householdbudget.app.ui.home.HomeScreen
import com.householdbudget.app.ui.ledger.LedgerScreen
import com.householdbudget.app.ui.recurring.RecurringRuleEditorScreen
import com.householdbudget.app.ui.recurring.RecurringRulesListScreen
import com.householdbudget.app.ui.settings.SettingsScreen
import com.householdbudget.app.ui.settings.categories.CategoryManagementScreen

private const val SETTINGS_MAIN = "main"
private const val SETTINGS_RECURRING_LIST = "recurring_list"
private const val SETTINGS_RECURRING_EDIT_PREFIX = "recurring_edit_"
private const val SETTINGS_CATEGORIES = "categories"

private const val NO_ARCHIVE_DETAIL = -1L

private data class TossTab(
    val icon: ImageVector,
    val labelRes: Int,
)

@Composable
fun MainTabScaffold(
    budgetViewModel: BudgetViewModel,
    calendarViewModel: CalendarViewModel,
    repository: BudgetRepository,
    onNavigateAdd: () -> Unit,
    onNavigateEdit: (Long) -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var settingsPane by rememberSaveable { mutableStateOf(SETTINGS_MAIN) }
    var archiveDetailId by rememberSaveable { mutableStateOf(NO_ARCHIVE_DETAIL) }
    var recurringAddNonce by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = archiveDetailId != NO_ARCHIVE_DETAIL) {
        archiveDetailId = NO_ARCHIVE_DETAIL
    }

    BackHandler(enabled = settingsPane != SETTINGS_MAIN) {
        settingsPane = when {
            settingsPane.startsWith(SETTINGS_RECURRING_EDIT_PREFIX) -> SETTINGS_RECURRING_LIST
            else -> SETTINGS_MAIN
        }
    }

    LaunchedEffect(selected) {
        if (selected != 3) {
            archiveDetailId = NO_ARCHIVE_DETAIL
        }
        if (selected != 4) {
            settingsPane = SETTINGS_MAIN
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selected <= 2) {
                FloatingActionButton(
                    onClick = onNavigateAdd,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation =
                        FloatingActionButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 10.dp,
                        ),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.fab_add_transaction),
                    )
                }
            }
        },
        bottomBar = {
            TossBottomBar(
                selected = selected,
                onSelect = { selected = it },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (selected) {
            0 -> HomeScreen(budgetViewModel = budgetViewModel, modifier = modifier)
            1 ->
                LedgerScreen(
                    budgetViewModel = budgetViewModel,
                    onTransactionClick = onNavigateEdit,
                    modifier = modifier,
                )
            2 ->
                CalendarScreen(
                    viewModel = calendarViewModel,
                    repository = repository,
                    modifier = modifier,
                )
            3 ->
                if (archiveDetailId == NO_ARCHIVE_DETAIL) {
                    ArchiveListScreen(
                        repository = repository,
                        onOpenDetail = { archiveDetailId = it },
                        modifier = modifier,
                    )
                } else {
                    ArchiveDetailScreen(
                        archiveId = archiveDetailId,
                        repository = repository,
                        onBack = { archiveDetailId = NO_ARCHIVE_DETAIL },
                        modifier = modifier,
                    )
                }
            else ->
                when {
                    settingsPane == SETTINGS_MAIN ->
                        SettingsScreen(
                            budgetViewModel = budgetViewModel,
                            onOpenRecurringRules = { settingsPane = SETTINGS_RECURRING_LIST },
                            onOpenCategoryManagement = { settingsPane = SETTINGS_CATEGORIES },
                            modifier = modifier,
                        )
                    settingsPane == SETTINGS_CATEGORIES ->
                        CategoryManagementScreen(
                            repository = repository,
                            onBack = { settingsPane = SETTINGS_MAIN },
                            modifier = modifier,
                        )
                    settingsPane == SETTINGS_RECURRING_LIST ->
                        RecurringRulesListScreen(
                            repository = repository,
                            onBack = { settingsPane = SETTINGS_MAIN },
                            onAdd = {
                                recurringAddNonce++
                                settingsPane = "${SETTINGS_RECURRING_EDIT_PREFIX}new"
                            },
                            onEdit = { id -> settingsPane = "${SETTINGS_RECURRING_EDIT_PREFIX}$id" },
                            modifier = modifier,
                        )
                    settingsPane.startsWith(SETTINGS_RECURRING_EDIT_PREFIX) -> {
                        val suffix = settingsPane.removePrefix(SETTINGS_RECURRING_EDIT_PREFIX)
                        val ruleId: Long? =
                            when (suffix) {
                                "new" -> null
                                else -> suffix.toLongOrNull()
                            }
                        if (ruleId == null && suffix != "new") {
                            RecurringRulesListScreen(
                                repository = repository,
                                onBack = { settingsPane = SETTINGS_MAIN },
                                onAdd = {
                                    recurringAddNonce++
                                    settingsPane = "${SETTINGS_RECURRING_EDIT_PREFIX}new"
                                },
                                onEdit = { id -> settingsPane = "${SETTINGS_RECURRING_EDIT_PREFIX}$id" },
                                modifier = modifier,
                            )
                        } else {
                            RecurringRuleEditorScreen(
                                budgetViewModel = budgetViewModel,
                                repository = repository,
                                ruleId = ruleId,
                                onBack = { settingsPane = SETTINGS_RECURRING_LIST },
                                onSaved = { settingsPane = SETTINGS_RECURRING_LIST },
                                nonce = if (ruleId == null) recurringAddNonce else 0,
                                modifier = modifier,
                            )
                        }
                    }
                    else ->
                        SettingsScreen(
                            budgetViewModel = budgetViewModel,
                            onOpenRecurringRules = { settingsPane = SETTINGS_RECURRING_LIST },
                            onOpenCategoryManagement = { settingsPane = SETTINGS_CATEGORIES },
                            modifier = modifier,
                        )
                }
        }
    }
}

/**
 * Toss-style bottom navigation: a flat white bar with a hairline top divider,
 * outlined icons that fill with the brand blue and gently pop on selection.
 * No Material pill indicator — clean and minimal.
 */
@Composable
private fun TossBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val tabs =
        remember {
            listOf(
                TossTab(Icons.Rounded.Home, R.string.nav_home),
                TossTab(Icons.Rounded.Receipt, R.string.nav_ledger),
                TossTab(Icons.Rounded.CalendarMonth, R.string.nav_calendar),
                TossTab(Icons.Rounded.Inventory2, R.string.nav_archive),
                TossTab(Icons.Rounded.Settings, R.string.nav_settings),
            )
        }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(62.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    TossNavItem(
                        tab = tab,
                        selected = selected == index,
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TossNavItem(
    tab: TossTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        label = "navItemColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "navItemScale",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            Modifier
                .weight(1f)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                )
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier =
                Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
        )
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
