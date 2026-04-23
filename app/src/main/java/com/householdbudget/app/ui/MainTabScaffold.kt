package com.householdbudget.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

    LaunchedEffect(selected) {
        if (selected != 3) {
            archiveDetailId = NO_ARCHIVE_DETAIL
        }
        if (selected != 4) {
            settingsPane = SETTINGS_MAIN
        }
    }

    // Terracotta indicator, no cool colors
    val navItemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selected <= 2) {
                FloatingActionButton(
                    onClick = onNavigateAdd,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    elevation =
                        FloatingActionButtonDefaults.elevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp,
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
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = selected == 0,
                    onClick = { selected = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_home), style = MaterialTheme.typography.labelMedium) },
                    colors = navItemColors,
                )
                NavigationBarItem(
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_ledger), style = MaterialTheme.typography.labelMedium) },
                    colors = navItemColors,
                )
                NavigationBarItem(
                    selected = selected == 2,
                    onClick = { selected = 2 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_calendar), style = MaterialTheme.typography.labelMedium) },
                    colors = navItemColors,
                )
                NavigationBarItem(
                    selected = selected == 3,
                    onClick = { selected = 3 },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_archive), style = MaterialTheme.typography.labelMedium) },
                    colors = navItemColors,
                )
                NavigationBarItem(
                    selected = selected == 4,
                    onClick = { selected = 4 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.labelMedium) },
                    colors = navItemColors,
                )
            }
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
                            onAdd = { settingsPane = "${SETTINGS_RECURRING_EDIT_PREFIX}new" },
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
                                onAdd = { settingsPane = "${SETTINGS_RECURRING_EDIT_PREFIX}new" },
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
