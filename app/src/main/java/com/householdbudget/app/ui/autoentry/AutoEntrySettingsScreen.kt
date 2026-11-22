package com.householdbudget.app.ui.autoentry

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.BudgetApplication
import com.householdbudget.app.R
import com.householdbudget.app.ui.components.AppTopBar
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.components.SectionHeader
import com.householdbudget.app.ui.components.pushedBottomPadding
import com.householdbudget.app.ui.settings.SettingsActionRow
import com.householdbudget.app.ui.settings.SettingsGroupPanel
import com.householdbudget.app.ui.settings.SettingsRowDivider
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatShortDayLabel
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun AutoEntrySettingsScreen(
    onBack: () -> Unit,
    onOpenPending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as BudgetApplication).container }
    val repo = container.autoEntryRepository
    val scope = rememberCoroutineScope()

    var accessGranted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        accessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        onPauseOrDispose {}
    }

    val pendingCount by remember(repo) { repo.observePendingCount() }.collectAsStateWithLifecycle(initialValue = 0)
    val rules by remember(repo) { repo.observeRules() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val ignored by remember(repo) { repo.observeIgnored() }.collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = pushedBottomPadding()),
    ) {
        item {
            AppTopBar(title = stringResource(R.string.auto_title), onBack = onBack)
        }

        item {
            SectionHeader(
                title = stringResource(R.string.auto_access_title),
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
            SettingsGroupPanel {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(if (accessGranted) R.string.auto_access_on else R.string.auto_access_off),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = {
                        // 일부 기기에는 알림 접근 설정 화면이 없어서 일반 설정으로 대신 연다.
                        runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                    }) { Text(stringResource(R.string.auto_access_open)) }
                }
                SettingsRowDivider()
                SettingsActionRow(
                    icon = Icons.Filled.PendingActions,
                    iconContainer = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    title = stringResource(R.string.auto_pending_row, pendingCount),
                    subtitle = null,
                    onClick = onOpenPending,
                )
            }
            Spacer(Modifier.height(Space.xxl))
        }

        item {
            SectionHeader(
                title = stringResource(R.string.auto_rules_title),
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )
            Spacer(Modifier.height(Space.sm))
        }
        if (rules.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.auto_rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
            }
        } else {
            items(rules, key = { it.merchantKey + "|" + it.direction }) { row ->
                val dir = stringResource(
                    when (row.direction) {
                        "OUT" -> R.string.auto_dir_out
                        "IN" -> R.string.auto_dir_in
                        "REFUND" -> R.string.auto_dir_refund
                        else -> R.string.auto_dir_unknown
                    },
                )
                val target = if (row.categoryId == null) {
                    stringResource(R.string.auto_rule_no_record)
                } else {
                    listOfNotNull(row.parentCategoryName, row.categoryName).joinToString(" · ")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHorizontalPadding, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${row.displayName} · $dir",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch { repo.deleteRule(row.merchantKey, row.direction) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.auto_rule_delete))
                    }
                }
            }
        }

        if (ignored.isNotEmpty()) {
            item {
                Spacer(Modifier.height(Space.xxl))
                SectionHeader(
                    title = stringResource(R.string.auto_ignored_title),
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = stringResource(R.string.auto_ignored_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
            }
            items(ignored, key = { "ignored_${it.id}" }) { e ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHorizontalPadding, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                e.merchant.ifBlank { stringResource(R.string.auto_pending_no_name) },
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(e.amountMinor.formatWon(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            LocalDate.ofEpochDay(e.occurredEpochDay).formatShortDayLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { scope.launch { repo.restoreIgnored(e.id) } }) {
                        Text(stringResource(R.string.auto_ignored_restore))
                    }
                }
            }
        }
    }
}
