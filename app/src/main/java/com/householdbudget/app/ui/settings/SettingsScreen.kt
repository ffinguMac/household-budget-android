package com.householdbudget.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.R
import com.householdbudget.app.data.export.CsvExporter
import com.householdbudget.app.data.work.ReminderScheduler
import com.householdbudget.app.domain.PeriodResolver
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.FabContentBottomPadding
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatMonthDayWeekday
import com.householdbudget.app.ui.util.formatWon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    budgetViewModel: BudgetViewModel,
    onOpenAutoEntry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArchive: (() -> Unit)? = null,
    onOpenBudget: (() -> Unit)? = null,
    onOpenCash: (() -> Unit)? = null,
    onExportCsv: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as BudgetApplication).container
    }
    val prefs = container.userPreferencesRepository
    val repository = container.budgetRepository
    val scope = rememberCoroutineScope()
    val pendingCount by remember(container) { container.autoEntryRepository.observePendingCount() }
        .collectAsStateWithLifecycle(initialValue = 0)

    val payday by budgetViewModel.paydayDom.collectAsStateWithLifecycle()
    val today by repository.today.collectAsStateWithLifecycle()
    val nextPayday = remember(payday, today) {
        PeriodResolver().periodContaining(today, payday).endExclusive
    }
    val monthlyBudgetMinor by prefs.monthlyBudgetMinor.collectAsStateWithLifecycle(initialValue = null)
    val reminderEnabled by prefs.reminderEnabled.collectAsStateWithLifecycle(initialValue = false)
    val reminderHour by prefs.reminderHour.collectAsStateWithLifecycle(initialValue = 21)
    val darkTheme by prefs.darkTheme.collectAsStateWithLifecycle(initialValue = false)
    val appLockEnabled by prefs.appLockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val cashBalance by remember(repository) { repository.observeCashBalance() }.collectAsStateWithLifecycle(initialValue = 0L)

    var showPaydaySheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showReminderHourSheet by remember { mutableStateOf(false) }
    var reminderPermissionDenied by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    val paydaySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reminderSheetState = rememberModalBottomSheetState()
    val themeSheetState = rememberModalBottomSheetState()

    fun enableReminder() {
        reminderPermissionDenied = false
        scope.launch { prefs.setReminderEnabled(true) }
        ReminderScheduler.schedule(context, reminderHour)
        showReminderHourSheet = true
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableReminder()
            } else {
                reminderPermissionDenied = true
            }
        }

    fun toggleReminder(checked: Boolean) {
        if (checked) {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                enableReminder()
            }
        } else {
            scope.launch { prefs.setReminderEnabled(false) }
            ReminderScheduler.cancel(context)
        }
    }

    fun exportCsv() {
        if (exporting) return
        exporting = true
        exportFailed = false
        scope.launch {
            runCatching {
                // 내보내는 순간의 기간을 쓴다(자정을 넘겨 켜 둔 경우 대비).
                repository.refreshToday()
                val period = repository.currentPeriod.first()
                val intent =
                    CsvExporter.exportTransactionsCsv(
                        context = context,
                        repository = repository,
                        start = period.startInclusive,
                        endInclusive = period.endExclusive.minusDays(1),
                    )
                context.startActivity(intent)
            }.onFailure { exportFailed = true }
            exporting = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = FabContentBottomPadding),
    ) {
        item {
            AppTopBar(title = stringResource(R.string.nav_settings))
        }

        // ── 기본 ─────────────────────────────────────────────────────────────
        item {
            Text(
                text = stringResource(R.string.settings_group_basic),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
            SettingsGroupPanel {
                SettingsActionRow(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.settings_payday_title_v2),
                    subtitle = stringResource(R.string.settings_payday_subtitle_v2, payday, nextPayday.formatMonthDayWeekday()),
                    onClick = { showPaydaySheet = true },
                )
                SettingsRowDivider()
                SettingsActionRow(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = stringResource(if (darkTheme) R.string.settings_theme_dark else R.string.settings_theme_light),
                    onClick = { showThemeSheet = true },
                )
                if (onOpenBudget != null) {
                    SettingsRowDivider()
                    SettingsActionRow(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = stringResource(R.string.settings_budget_title),
                        subtitle = monthlyBudgetMinor.let { budget ->
                            if (budget != null) {
                                stringResource(R.string.settings_budget_subtitle_set, budget.formatWon())
                            } else {
                                stringResource(R.string.settings_budget_subtitle_unset)
                            }
                        },
                        onClick = onOpenBudget,
                    )
                }
                if (onOpenCash != null) {
                    SettingsRowDivider()
                    SettingsActionRow(
                        icon = Icons.Filled.Savings,
                        title = stringResource(R.string.cash_title),
                        subtitle = stringResource(R.string.cash_row_subtitle),
                        onClick = onOpenCash,
                        trailing = {
                            Text(
                                text = cashBalance.formatWon(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Space.xxl)) }

        // ── 자동화 · 알림 ─────────────────────────────────────────────────────
        item {
            Text(
                text = stringResource(R.string.settings_group_automation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
            SettingsGroupPanel {
                SettingsActionRow(
                    icon = Icons.Filled.Autorenew,
                    title = stringResource(R.string.auto_title),
                    subtitle = if (pendingCount > 0) {
                        stringResource(R.string.auto_row_subtitle_pending, pendingCount)
                    } else {
                        stringResource(R.string.auto_row_subtitle_idle)
                    },
                    onClick = onOpenAutoEntry,
                    trailing = if (pendingCount > 0) {
                        {
                            Box(
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = pendingCount.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    } else null,
                )
                SettingsRowDivider()
                SettingsActionRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_reminder_title),
                    subtitle = when {
                        reminderPermissionDenied -> stringResource(R.string.settings_reminder_denied)
                        reminderEnabled -> stringResource(R.string.settings_reminder_subtitle_on, reminderHour)
                        else -> stringResource(R.string.settings_reminder_subtitle_off)
                    },
                    onClick = {
                        if (reminderEnabled) {
                            showReminderHourSheet = true
                        } else {
                            toggleReminder(true)
                        }
                    },
                    trailing = {
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { toggleReminder(it) },
                            colors = settingsSwitchColors(),
                        )
                    },
                )
            }
        }

        item { Spacer(Modifier.height(Space.xxl)) }

        // ── 데이터 ───────────────────────────────────────────────────────────
        item {
            Text(
                text = stringResource(R.string.settings_group_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
            SettingsGroupPanel {
                if (onOpenArchive != null) {
                    SettingsActionRow(
                        icon = Icons.Filled.Inventory2,
                        title = stringResource(R.string.archive_title),
                        subtitle = null,
                        onClick = onOpenArchive,
                    )
                    SettingsRowDivider()
                }
                SettingsActionRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.settings_export_title),
                    subtitle = when {
                        exporting -> stringResource(R.string.settings_export_running)
                        exportFailed -> stringResource(R.string.settings_export_error)
                        else -> stringResource(R.string.settings_export_subtitle)
                    },
                    onClick = onExportCsv ?: ::exportCsv,
                )
            }
        }

        item { Spacer(Modifier.height(Space.xxl)) }

        // ── 보안 ─────────────────────────────────────────────────────────────
        item {
            Text(
                text = stringResource(R.string.settings_group_security),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
            SettingsGroupPanel {
                SettingsActionRow(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.settings_lock_title),
                    subtitle = stringResource(R.string.settings_lock_subtitle),
                    onClick = { scope.launch { prefs.setAppLockEnabled(!appLockEnabled) } },
                    trailing = {
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { checked ->
                                scope.launch { prefs.setAppLockEnabled(checked) }
                            },
                            colors = settingsSwitchColors(),
                        )
                    },
                )
            }
        }
    }

    // ── 월급 받는 날 선택 바텀시트 ──────────────────────────────────────────
    if (showPaydaySheet) {
        ModalBottomSheet(
            onDismissRequest = { showPaydaySheet = false },
            sheetState = paydaySheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = ScreenHorizontalPadding)
                    .padding(bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(
                    text = stringResource(R.string.settings_payday_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_payday_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    (1..31).chunked(7).forEach { rowDays ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowDays.forEach { day ->
                                val selected = payday == day
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clickable {
                                            if (day != payday) {
                                                budgetViewModel.setPaydayDom(day)
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.settings_payday_changed, day),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                            showPaydaySheet = false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = day.toString(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                            repeat(7 - rowDays.size) {
                                Spacer(Modifier.weight(1f).height(48.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = themeSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.xxxl)
                    .selectableGroup(),
            ) {
                listOf(false to R.string.settings_theme_light, true to R.string.settings_theme_dark).forEach { (isDark, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = darkTheme == isDark,
                                role = Role.RadioButton,
                                onClick = {
                                    scope.launch { prefs.setDarkTheme(isDark) }
                                    showThemeSheet = false
                                },
                            )
                            .padding(horizontal = ScreenHorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (darkTheme == isDark) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // ── 리마인더 시간 선택 바텀시트 (0~23시 그리드) ─────────────────────────
    if (showReminderHourSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReminderHourSheet = false },
            sheetState = reminderSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding)
                    .padding(bottom = Space.xxxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(
                    text = stringResource(R.string.settings_reminder_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_reminder_sheet_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.xs))
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    (0..23).chunked(6).forEach { rowHours ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowHours.forEach { hour ->
                                val selected = reminderHour == hour
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                        .clickable {
                                            scope.launch { prefs.setReminderHour(hour) }
                                            ReminderScheduler.schedule(context, hour)
                                            showReminderHourSheet = false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_reminder_hour_cell, hour),
                                        style = if (selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
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

/** 설정 그룹을 감싸는 라운드 패널. */
@Composable
internal fun SettingsGroupPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column { content() }
    }
}

@Composable
internal fun SettingsRowDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = Space.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 좌측 아이콘 + 제목/부제 + 우측 chevron 또는 커스텀 trailing. 행 전체가 클릭 대상. */
@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(Space.sm)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surface,
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
