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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.ui.archive.ArchiveDetailScreen
import com.householdbudget.app.ui.archive.ArchiveListScreen
import com.householdbudget.app.ui.autoentry.AutoEntrySettingsScreen
import com.householdbudget.app.ui.autoentry.PendingReviewScreen
import com.householdbudget.app.ui.calendar.CalendarScreen
import com.householdbudget.app.ui.calendar.CalendarViewModel
import com.householdbudget.app.ui.feed.FeedScreen
import com.householdbudget.app.ui.settings.BudgetSettingsScreen
import com.householdbudget.app.ui.settings.SettingsScreen
import com.householdbudget.app.ui.stats.StatsScreen

/** 탭 전환 크로스페이드/슬라이드 지속시간. 짧고 절제된 토스 느낌. */
private const val TAB_TRANSITION_MS = 200

/** 탭 전환 시 수평 슬라이드 오프셋(px). 아주 약한 움직임만 준다. */
private const val TAB_SLIDE_OFFSET_PX = 40

/** 중앙 + 버튼 지름. */
private val AddButtonSize = 56.dp

/** 중앙 + 버튼이 바 위로 돌출되는 높이. */
private val AddButtonProtrusion = 16.dp

private enum class MainTab(
    val icon: ImageVector,
    val labelRes: Int,
) {
    FEED(Icons.Rounded.Home, R.string.nav_feed),
    CALENDAR(Icons.Rounded.CalendarMonth, R.string.nav_calendar),
    STATS(Icons.Rounded.BarChart, R.string.nav_stats),
    SETTINGS(Icons.Rounded.Settings, R.string.nav_settings),
}

private sealed interface SettingsPane {
    data object Main : SettingsPane

    data object Archive : SettingsPane

    data class ArchiveDetail(val archiveId: Long) : SettingsPane

    data object Budget : SettingsPane

    data object AutoEntry : SettingsPane

    data object Pending : SettingsPane
}

@Composable
fun MainTabScaffold(
    budgetViewModel: BudgetViewModel,
    calendarViewModel: CalendarViewModel,
    repository: BudgetRepository,
    onNavigateAdd: () -> Unit,
    onNavigateEdit: (Long) -> Unit,
    openPending: Boolean = false,
    onOpenPendingHandled: () -> Unit = {},
) {
    // enum 은 기본 Saver 가 없어 ordinal 로 저장하고 파생시킨다.
    var selectedOrdinal by rememberSaveable { mutableIntStateOf(MainTab.FEED.ordinal) }
    val selected = MainTab.entries[selectedOrdinal]
    val context = LocalContext.current
    val pendingCount by remember(context) {
        (context.applicationContext as BudgetApplication).container.autoEntryRepository.observePendingCount()
    }.collectAsStateWithLifecycle(initialValue = 0)
    // 프로세스 사망 복구는 포기 (탭 이탈 시 Main 으로 초기화되는 기존 동작과 동일).
    var settingsPane by remember { mutableStateOf<SettingsPane>(SettingsPane.Main) }

    // 탭 콘텐츠는 AnimatedContent 로 교체되며 컴포지션에서 빠지므로,
    // 스크롤 상태를 여기(바깥)에서 만들어 내려보내 탭 전환 후에도 위치를 보존한다.
    val feedListState = rememberLazyListState()
    val calendarListState = rememberLazyListState()
    val statsListState = rememberLazyListState()

    BackHandler(enabled = selected == MainTab.SETTINGS && settingsPane != SettingsPane.Main) {
        settingsPane =
            when (settingsPane) {
                is SettingsPane.ArchiveDetail -> SettingsPane.Archive
                SettingsPane.Pending -> SettingsPane.AutoEntry
                else -> SettingsPane.Main
            }
    }

    LaunchedEffect(selected) {
        if (selected != MainTab.SETTINGS) {
            settingsPane = SettingsPane.Main
        }
    }
    // 순서 중요: 위 LaunchedEffect(selected) 가 처음 컴포지션 때 settingsPane 을 Main 으로 되돌리므로 그 뒤에 둔다.
    LaunchedEffect(openPending) {
        if (openPending) {
            selectedOrdinal = MainTab.SETTINGS.ordinal
            settingsPane = SettingsPane.Pending
            onOpenPendingHandled()
        }
    }

    val pushed = selected == MainTab.SETTINGS && settingsPane != SettingsPane.Main

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!pushed) {
                AppBottomBar(
                    selected = selected,
                    onSelect = { selectedOrdinal = it.ordinal },
                    onAdd = onNavigateAdd,
                )
            }
        },
    ) { padding ->
        // 하단 인셋은 넘기지 않는다: 리스트가 바텀바 아래로 자연스럽게 스크롤되고,
        // 각 화면이 버튼/네비바 여백을 공용 FabContentBottomPadding 으로 직접 처리한다.
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
                MainTab.FEED ->
                    FeedScreen(
                        budgetViewModel = budgetViewModel,
                        onTransactionClick = onNavigateEdit,
                        listState = feedListState,
                        pendingCount = pendingCount,
                        onOpenPending = {
                            selectedOrdinal = MainTab.SETTINGS.ordinal
                            settingsPane = SettingsPane.Pending
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                MainTab.CALENDAR ->
                    CalendarScreen(
                        viewModel = calendarViewModel,
                        repository = repository,
                        listState = calendarListState,
                        modifier = Modifier.fillMaxSize(),
                    )
                MainTab.STATS ->
                    StatsScreen(
                        repository = repository,
                        budgetViewModel = budgetViewModel,
                        listState = statsListState,
                        modifier = Modifier.fillMaxSize(),
                    )
                MainTab.SETTINGS ->
                    when (val pane = settingsPane) {
                        SettingsPane.Main ->
                            SettingsScreen(
                                budgetViewModel = budgetViewModel,
                                onOpenArchive = { settingsPane = SettingsPane.Archive },
                                onOpenBudget = { settingsPane = SettingsPane.Budget },
                                onOpenAutoEntry = { settingsPane = SettingsPane.AutoEntry },
                                modifier = Modifier.fillMaxSize(),
                            )
                        SettingsPane.Archive ->
                            ArchiveListScreen(
                                repository = repository,
                                onOpenDetail = { settingsPane = SettingsPane.ArchiveDetail(archiveId = it) },
                                onBack = { settingsPane = SettingsPane.Main },
                                modifier = Modifier.fillMaxSize(),
                            )
                        is SettingsPane.ArchiveDetail ->
                            ArchiveDetailScreen(
                                archiveId = pane.archiveId,
                                repository = repository,
                                onBack = { settingsPane = SettingsPane.Archive },
                                modifier = Modifier.fillMaxSize(),
                            )
                        SettingsPane.AutoEntry ->
                            AutoEntrySettingsScreen(
                                onBack = { settingsPane = SettingsPane.Main },
                                onOpenPending = { settingsPane = SettingsPane.Pending },
                                modifier = Modifier.fillMaxSize(),
                            )
                        SettingsPane.Pending ->
                            PendingReviewScreen(
                                budgetViewModel = budgetViewModel,
                                onBack = { settingsPane = SettingsPane.AutoEntry },
                                modifier = Modifier.fillMaxSize(),
                            )
                        SettingsPane.Budget ->
                            BudgetSettingsScreen(
                                repository = repository,
                                budgetViewModel = budgetViewModel,
                                onBack = { settingsPane = SettingsPane.Main },
                                modifier = Modifier.fillMaxSize(),
                            )
                    }
            }
        }
    }
}

/**
 * 하단 바: 탭 4개 + 한가운데 살짝 돌출된 + 버튼.
 *
 * 버튼이 바 위로 [AddButtonProtrusion] 만큼 돌출되므로, Surface 를 그만큼 아래로 밀어
 * 버튼 전체가 bottomBar 영역 안에 남게 한다 (Surface 클리핑/히트테스트 문제 회피).
 * 돌출부 옆 투명 영역은 포인터 핸들러가 없어 터치가 아래 콘텐츠로 통과한다.
 */
@Composable
private fun AppBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    onAdd: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = AddButtonProtrusion),
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
                        NavItem(
                            tab = MainTab.FEED,
                            selected = selected == MainTab.FEED,
                            onClick = { onSelect(MainTab.FEED) },
                        )
                        NavItem(
                            tab = MainTab.CALENDAR,
                            selected = selected == MainTab.CALENDAR,
                            onClick = { onSelect(MainTab.CALENDAR) },
                        )
                        // 중앙 + 버튼 자리 비움 (버튼은 바깥 Box 에 오버레이).
                        Spacer(modifier = Modifier.weight(1f))
                        NavItem(
                            tab = MainTab.STATS,
                            selected = selected == MainTab.STATS,
                            onClick = { onSelect(MainTab.STATS) },
                        )
                        NavItem(
                            tab = MainTab.SETTINGS,
                            selected = selected == MainTab.SETTINGS,
                            onClick = { onSelect(MainTab.SETTINGS) },
                        )
                    }
                }
            }
        }

        // 중앙 + 버튼: 바 위로 살짝 돌출된 민트 원형.
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .size(AddButtonSize)
                    .shadow(
                        8.dp,
                        CircleShape,
                        ambientColor = MaterialTheme.colorScheme.primary,
                        spotColor = MaterialTheme.colorScheme.primary,
                    )
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.fab_add_transaction),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun RowScope.NavItem(
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
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
