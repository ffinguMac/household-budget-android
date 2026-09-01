package com.householdbudget.app.ui.settings

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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHeader
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader
import com.householdbudget.app.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    budgetViewModel: BudgetViewModel,
    onOpenRecurringRules: () -> Unit,
    onOpenCategoryManagement: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArchive: (() -> Unit)? = null,
) {
    val payday by budgetViewModel.paydayDom.collectAsStateWithLifecycle()
    val kbankCardEnabled by budgetViewModel.kbankCardEnabled.collectAsStateWithLifecycle()
    var showPaydaySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

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
                    title = stringResource(R.string.settings_payday_title),
                    subtitle = stringResource(R.string.settings_payday_value, payday),
                    onClick = { showPaydaySheet = true },
                )
            }
        }

        item { Spacer(Modifier.height(Space.xxl)) }

        // ── 자동화 ───────────────────────────────────────────────────────────
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
                    icon = Icons.Filled.CreditCard,
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = stringResource(R.string.settings_kbank_card_title),
                    subtitle = stringResource(R.string.settings_kbank_card_hint),
                    onClick = { budgetViewModel.setKbankCardEnabled(!kbankCardEnabled) },
                    trailing = {
                        Switch(
                            checked = kbankCardEnabled,
                            onCheckedChange = { budgetViewModel.setKbankCardEnabled(it) },
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
            }
        }
    }

    // ── 월급일 선택 바텀시트 ─────────────────────────────────────────────────
    if (showPaydaySheet) {
        ModalBottomSheet(
            onDismissRequest = { showPaydaySheet = false },
            sheetState = sheetState,
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
