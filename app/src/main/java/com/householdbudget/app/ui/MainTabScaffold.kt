package com.householdbudget.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalLayoutDirection
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

/** 탭 전환 크로스페이드/슬라이드 지속시간. 짧고 절제된 토스 느낌. */
private const val TAB_TRANSITION_MS = 200

/** 탭 전환 시 수평 슬라이드 오프셋(px). 아주 약한 움직임만 준다. */
private const val TAB_SLIDE_OFFSET_PX = 40

private enum class MainTab(
    val icon: ImageVector,
    val labelRes: Int,
    val showsFab: Boolean,
) {
    HOME(Icons.Rounded.Home, R.string.nav_home, true),
    LEDGER(Icons.Rounded.Receipt, R.string.nav_ledger, true),
    CALENDAR(Icons.Rounded.CalendarMonth, R.string.nav_calendar, true),
    SETTINGS(Icons.Rounded.Settings, R.string.nav_settings, false),
}

private sealed interface SettingsPane {
    data object Main : SettingsPane

    data object Categories : SettingsPane

    data object RecurringList : SettingsPane

    data class RecurringEdit(val ruleId: Long?) : SettingsPane

    data object Archive : SettingsPane

    data class ArchiveDetail(val archiveId: Long) : SettingsPane
}

@Composable
fun MainTabScaffold(
    budgetViewModel: BudgetViewModel,
    calendarViewModel: CalendarViewModel,
    repository: BudgetRepository,
    onNavigateAdd: () -> Unit,
    onNavigateEdit: (Long) -> Unit,
) {
    // enum 은 기본 Saver 가 없어 ordinal 로 저장하고 파생시킨다.
    var selectedOrdinal by rememberSaveable { mutableIntStateOf(MainTab.HOME.ordinal) }
    val selected = MainTab.entries[selectedOrdinal]
    // 프로세스 사망 복구는 포기 (탭 이탈 시 Main 으로 초기화되는 기존 동작과 동일).
    var settingsPane by remember { mutableStateOf<SettingsPane>(SettingsPane.Main) }
    var recurringAddNonce by rememberSaveable { mutableIntStateOf(0) }

    // 탭 콘텐츠는 AnimatedContent 로 교체되며 컴포지션에서 빠지므로,
    // 스크롤 상태를 여기(바깥)에서 만들어 내려보내 탭 전환 후에도 위치를 보존한다.
    val homeScrollState = rememberScrollState()
    val ledgerListState = rememberLazyListState()
    val calendarListState = rememberLazyListState()

    BackHandler(enabled = selected == MainTab.SETTINGS && settingsPane != SettingsPane.Main) {
        settingsPane =
            when (settingsPane) {
                is SettingsPane.RecurringEdit -> SettingsPane.RecurringList
                is SettingsPane.ArchiveDetail -> SettingsPane.Archive
                else -> SettingsPane.Main
            }
    }

    LaunchedEffect(selected) {
        if (selected != MainTab.SETTINGS) {
            settingsPane = SettingsPane.Main
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selected.showsFab) {
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
                onSelect = { selectedOrdinal = it.ordinal },
            )
        },
    ) { padding ->
        // 하단 인셋은 넘기지 않는다: 리스트가 바텀바 아래로 자연스럽게 스크롤되고,
        // 각 화면이 FAB/네비바 여백을 공용 FabContentBottomPadding 으로 직접 처리한다.
        val layoutDirection = LocalLayoutDirection.current
        val contentModifier =
            Modifier.padding(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
            )
        AnimatedContent(
            targetState = selected,
            modifier = contentModifier,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                (
                    fadeIn(animationSpec = tween(TAB_TRANSITION_MS)) +
                        slideInHorizontally(animationSpec = tween(TAB_TRANSITION_MS)) {
                            if (forward) TAB_SLIDE_OFFSET_PX else -TAB_SLIDE_OFFSET_PX
                        }
                ).togetherWith(
                    fadeOut(animationSpec = tween(TAB_TRANSITION_MS)) +
                        slideOutHorizontally(animationSpec = tween(TAB_TRANSITION_MS)) {
                            if (forward) -TAB_SLIDE_OFFSET_PX else TAB_SLIDE_OFFSET_PX
                        },
                )
            },
            label = "mainTabContent",
        ) { tab ->
            when (tab) {
                MainTab.HOME ->
                    HomeScreen(
                        budgetViewModel = budgetViewModel,
                        onSeeAllTransactions = { selectedOrdinal = MainTab.LEDGER.ordinal },
                        scrollState = homeScrollState,
                        modifier = Modifier.fillMaxSize(),
                    )
                MainTab.LEDGER ->
                    LedgerScreen(
                        budgetViewModel = budgetViewModel,
                        onTransactionClick = onNavigateEdit,
                        listState = ledgerListState,
                        modifier = Modifier.fillMaxSize(),
                    )
                MainTab.CALENDAR ->
                    CalendarScreen(
                        viewModel = calendarViewModel,
                        repository = repository,
                        listState = calendarListState,
                        modifier = Modifier.fillMaxSize(),
                    )
                MainTab.SETTINGS ->
                    when (val pane = settingsPane) {
                        SettingsPane.Main ->
                            SettingsScreen(
                                budgetViewModel = budgetViewModel,
                                onOpenRecurringRules = { settingsPane = SettingsPane.RecurringList },
                                onOpenCategoryManagement = { settingsPane = SettingsPane.Categories },
                                onOpenArchive = { settingsPane = SettingsPane.Archive },
                                modifier = Modifier.fillMaxSize(),
                            )
                        SettingsPane.Categories ->
                            CategoryManagementScreen(
                                repository = repository,
                                onBack = { settingsPane = SettingsPane.Main },
                                modifier = Modifier.fillMaxSize(),
                            )
                        SettingsPane.RecurringList ->
                            RecurringRulesListScreen(
                                repository = repository,
                                onBack = { settingsPane = SettingsPane.Main },
                                onAdd = {
                                    recurringAddNonce++
                                    settingsPane = SettingsPane.RecurringEdit(ruleId = null)
                                },
                                onEdit = { id -> settingsPane = SettingsPane.RecurringEdit(ruleId = id) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        is SettingsPane.RecurringEdit ->
                            RecurringRuleEditorScreen(
                                budgetViewModel = budgetViewModel,
                                repository = repository,
                                ruleId = pane.ruleId,
                                onBack = { settingsPane = SettingsPane.RecurringList },
                                onSaved = { settingsPane = SettingsPane.RecurringList },
                                nonce = if (pane.ruleId == null) recurringAddNonce else 0,
                                modifier = Modifier.fillMaxSize(),
                            )
                        SettingsPane.Archive ->
                            ArchiveListScreen(
                                repository = repository,
                                onOpenDetail = { settingsPane = SettingsPane.ArchiveDetail(archiveId = it) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        is SettingsPane.ArchiveDetail ->
                            ArchiveDetailScreen(
                                archiveId = pane.archiveId,
                                repository = repository,
                                onBack = { settingsPane = SettingsPane.Archive },
                                modifier = Modifier.fillMaxSize(),
                            )
                    }
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
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
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
                MainTab.entries.forEach { tab ->
                    TossNavItem(
                        tab = tab,
                        selected = selected == tab,
                        onClick = { onSelect(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TossNavItem(
    tab: MainTab,
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
    // ripple 대신 아주 약한 알파 반응으로 눌림 피드백을 준다.
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.55f else 1f,
        label = "navItemPressAlpha",
    )

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
                .graphicsLayer { alpha = pressAlpha }
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
