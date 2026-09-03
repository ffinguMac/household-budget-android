package com.householdbudget.app.ui.settings

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.R
import com.householdbudget.app.data.export.CsvExporter
import com.householdbudget.app.data.work.ReminderScheduler
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHeader
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatWon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    budgetViewModel: BudgetViewModel,
    onOpenRecurringRules: () -> Unit,
    onOpenCategoryManagement: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArchive: (() -> Unit)? = null,
    onOpenBudget: (() -> Unit)? = null,
    onExportCsv: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as BudgetApplication).container
    }
    val prefs = container.userPreferencesRepository
    val repository = container.budgetRepository
    val scope = rememberCoroutineScope()

    val payday by budgetViewModel.paydayDom.collectAsStateWithLifecycle()
    val monthlyBudgetMinor by prefs.monthlyBudgetMinor.collectAsStateWithLifecycle(initialValue = null)
    val reminderEnabled by prefs.reminderEnabled.collectAsStateWithLifecycle(initialValue = false)
    val reminderHour by prefs.reminderHour.collectAsStateWithLifecycle(initialValue = 21)
    val appLockEnabled by prefs.appLockEnabled.collectAsStateWithLifecycle(initialValue = false)

    var showPaydaySheet by remember { mutableStateOf(false) }
    var showReminderHourSheet by remember { mutableStateOf(false) }
    var reminderPermissionDenied by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    val paydaySheetState = rememberModalBottomSheetState()
    val reminderSheetState = rememberModalBottomSheetState()

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
                val period = budgetViewModel.homeSummary.value.period
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
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_settings),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── 기본 ─────────────────────────────────────────────────────────────
        item {
            SectionHeader(
                title = stringResource(R.string.settings_group_basic),
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
            SettingsGroupPanel {
                SettingsActionRow(
                    icon = Icons.Filled.DateRange,
                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.settings_payday_title_v2),
                    subtitle = stringResource(R.string.settings_payday_subtitle_v2, payday),
                    onClick = { showPaydaySheet = true },
                )
                if (onOpenBudget != null) {
                    SettingsRowDivider()
                    SettingsActionRow(
                        icon = Icons.Filled.AccountBalanceWallet,
                        iconContainer = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
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
            }
        }

        item { Spacer(Modifier.height(Space.xxl)) }

        // ── 자동화 · 알림 ─────────────────────────────────────────────────────
        item {
            SectionHeader(
                title = stringResource(R.string.settings_group_automation),
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
            SettingsGroupPanel {
                SettingsActionRow(
                    icon = Icons.Filled.Autorenew,
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = stringResource(R.string.settings_open_recurring),
                    subtitle = stringResource(R.string.settings_recurring_subtitle),
                    onClick = onOpenRecurringRules,
                )
                SettingsRowDivider()
                SettingsActionRow(
                    icon = Icons.Filled.Notifications,
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
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
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                )
            }
        }

        item { Spacer(Modifier.height(Space.xxl)) }

        // ── 데이터 ───────────────────────────────────────────────────────────
        item {
            SectionHeader(
                title = stringResource(R.string.settings_group_data),
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
            SettingsGroupPanel {
                SettingsActionRow(
                    icon = Icons.Filled.Category,
                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.settings_open_categories),
                    subtitle = stringResource(R.string.settings_open_categories_subtitle),
                    onClick = onOpenCategoryManagement,
                )
                if (onOpenArchive != null) {
                    SettingsRowDivider()
                    SettingsActionRow(
                        icon = Icons.Filled.Inventory2,
                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.archive_title),
                        subtitle = null,
                        onClick = onOpenArchive,
                    )
                }
                SettingsRowDivider()
                SettingsActionRow(
                    icon = Icons.Filled.Share,
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
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
            SectionHeader(
                title = stringResource(R.string.settings_group_security),
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
            SettingsGroupPanel {
                SettingsActionRow(
                    icon = Icons.Filled.Lock,
                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.settings_lock_title),
                    subtitle = stringResource(R.string.settings_lock_subtitle),
                    onClick = { scope.launch { prefs.setAppLockEnabled(!appLockEnabled) } },
                    trailing = {
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { checked ->
                                scope.launch { prefs.setAppLockEnabled(checked) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
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
                    .padding(horizontal = ScreenHorizontalPadding)
                    .padding(bottom = Space.xxxl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(
                    text = stringResource(R.string.settings_payday_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_payday_select),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_payday_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.xs))
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    (1..31).chunked(7).forEach { rowDays ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowDays.forEach { day ->
                                val selected = payday == day
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainer,
                                        )
                                        .clickable {
                                            budgetViewModel.setPaydayDom(day)
                                            showPaydaySheet = false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            repeat(7 - rowDays.size) {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
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
                    fontWeight = FontWeight.Bold,
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
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainer,
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
private fun SettingsGroupPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRowDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = Space.lg),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}

/** 좌측 아이콘 + 제목/부제 + 우측 chevron 또는 커스텀 trailing. 행 전체가 클릭 대상. */
@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
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
            shape = MaterialTheme.shapes.medium,
            color = iconContainer,
            tonalElevation = 0.dp,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(Space.sm)
                    .size(20.dp),
                tint = iconTint,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
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
