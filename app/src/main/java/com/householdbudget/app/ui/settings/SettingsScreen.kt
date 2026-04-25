package com.householdbudget.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.householdbudget.app.data.preferences.Profile
import com.householdbudget.app.data.preferences.ProfileManager
import com.householdbudget.app.ui.BackupStatus
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    budgetViewModel: BudgetViewModel,
    onOpenRecurringRules: () -> Unit,
    onOpenCategoryManagement: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val app = context.applicationContext as? BudgetApplication
    val scope = rememberCoroutineScope()

    val payday by budgetViewModel.paydayDom.collectAsStateWithLifecycle()
    val kbankCardEnabled by budgetViewModel.kbankCardEnabled.collectAsStateWithLifecycle()
    val backupStatus by budgetViewModel.backupStatus.collectAsStateWithLifecycle()
    var showSavedFeedback by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // ── 프로필 상태 ───────────────────────────────────────────────────────────
    val profiles by (app?.profileManager?.profiles
        ?: kotlinx.coroutines.flow.flowOf(emptyList<Profile>())).collectAsStateWithLifecycle(emptyList())
    val currentProfileId by (app?.profileManager?.currentProfileId
        ?: kotlinx.coroutines.flow.flowOf(ProfileManager.DEFAULT_PROFILE_ID)).collectAsStateWithLifecycle(ProfileManager.DEFAULT_PROFILE_ID)

    var showProfileDialog by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var deleteConfirmProfileId by remember { mutableStateOf<Long?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) budgetViewModel.exportBackup(context, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

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

        // ── 프로필 섹션 ──────────────────────────────────────────────────────
        item {
            val currentProfile = profiles.firstOrNull { it.id == currentProfileId }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_profile_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProfileDialog = true },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp).size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentProfile?.name ?: "기본",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.settings_profile_subtitle, profiles.size),
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

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            (1..31).toList().chunked(7).forEach { rowDays ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    rowDays.forEach { day ->
                                        val isSelected = payday == day
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clickable {
                                                    budgetViewModel.setPaydayDom(day)
                                                    showSavedFeedback = true
                                                },
                                            shape = MaterialTheme.shapes.medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainer,
                                            tonalElevation = 0.dp,
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = day.toString(),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                                        else MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                    repeat(7 - rowDays.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
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
                                imageVector = Icons.Filled.InsertChart,
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

        item { Spacer(Modifier.height(16.dp)) }

        // ── 백업 / 복원 섹션 ─────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "데이터 백업 / 복원",
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
                        Text(
                            text = "모든 거래·카테고리·예산 설정을 JSON 파일로 저장하고 복원합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = {
                                    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                                    exportLauncher.launch("가계부_백업_$date.json")
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                enabled = backupStatus !is BackupStatus.Working,
                            ) {
                                Icon(
                                    Icons.Filled.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("내보내기", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.weight(1f),
                                enabled = backupStatus !is BackupStatus.Working,
                            ) {
                                Icon(
                                    Icons.Filled.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("복원", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        when (val s = backupStatus) {
                            is BackupStatus.Working -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text("처리 중...", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            is BackupStatus.ExportDone -> Text(
                                "✅ 내보내기 완료",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            is BackupStatus.ImportDone -> Text(
                                "✅ 복원 완료 — 앱을 재시작하면 반영됩니다.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            is BackupStatus.Error -> Text(
                                "❌ 오류: ${s.message}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            else -> {}
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    // ── 프로필 관리 다이얼로그 ───────────────────────────────────────────────
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text(stringResource(R.string.settings_profile_manage)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    profiles.forEachIndexed { index, profile ->
                        if (index > 0) HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = profile.id != currentProfileId) {
                                    scope.launch {
                                        app?.profileManager?.setCurrentProfile(profile.id)
                                        app?.reinitializeForProfile(profile.id)
                                        showProfileDialog = false
                                        activity?.recreate()
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (profile.id == currentProfileId) Icons.Filled.Check
                                    else Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = if (profile.id == currentProfileId) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (profile.id == currentProfileId) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                color = if (profile.id == currentProfileId) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            if (profile.id != ProfileManager.DEFAULT_PROFILE_ID && profile.id != currentProfileId) {
                                IconButton(
                                    onClick = { deleteConfirmProfileId = profile.id },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.settings_profile_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(
                        onClick = {
                            newProfileName = ""
                            showAddProfileDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.settings_profile_add))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text(stringResource(R.string.settings_profile_close))
                }
            },
        )
    }

    // ── 프로필 추가 다이얼로그 ────────────────────────────────────────────────
    if (showAddProfileDialog) {
        AlertDialog(
            onDismissRequest = { showAddProfileDialog = false },
            title = { Text(stringResource(R.string.settings_profile_add)) },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text(stringResource(R.string.settings_profile_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newProfileName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                app?.profileManager?.addProfile(name)
                                showAddProfileDialog = false
                                newProfileName = ""
                            }
                        }
                    },
                    enabled = newProfileName.trim().isNotEmpty(),
                ) { Text(stringResource(R.string.settings_profile_add_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddProfileDialog = false }) {
                    Text(stringResource(R.string.settings_profile_cancel))
                }
            },
        )
    }

    // ── 프로필 삭제 확인 다이얼로그 ──────────────────────────────────────────
    deleteConfirmProfileId?.let { targetId ->
        val targetName = profiles.firstOrNull { it.id == targetId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deleteConfirmProfileId = null },
            title = { Text(stringResource(R.string.settings_profile_delete)) },
            text = { Text(stringResource(R.string.settings_profile_delete_confirm, targetName)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app?.profileManager?.deleteProfile(targetId)
                        deleteConfirmProfileId = null
                    }
                }) { Text(stringResource(R.string.settings_profile_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmProfileId = null }) {
                    Text(stringResource(R.string.settings_profile_cancel))
                }
            },
        )
    }

    // ── 복원 확인 다이얼로그 ─────────────────────────────────────────────────
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("데이터 복원") },
            text = { Text("현재 모든 데이터가 백업 파일로 교체됩니다.\n이 작업은 되돌릴 수 없습니다. 계속할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    budgetViewModel.importBackup(context, uri)
                    pendingImportUri = null
                }) { Text("복원", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("취소") }
            },
        )
    }

    // 완료 메시지 자동 초기화
    LaunchedEffect(backupStatus) {
        if (backupStatus is BackupStatus.ExportDone || backupStatus is BackupStatus.ImportDone) {
            delay(4_000)
            budgetViewModel.clearBackupStatus()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = MaterialTheme.shapes.medium,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cashback_category_auto)) },
                    onClick = {
                        budgetViewModel.setCashbackCategoryId(null)
                        expanded = false
                    },
                )
                incomeParents.forEach { parent ->
                    val leaves = childrenByParent[parent.id].orEmpty()
                    leaves.forEach { leaf ->
                        DropdownMenuItem(
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
