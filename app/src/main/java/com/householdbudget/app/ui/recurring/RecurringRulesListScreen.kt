package com.householdbudget.app.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.ui.components.EmptyState
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.kindAccent
import com.householdbudget.app.ui.theme.kindSignPrefix
import com.householdbudget.app.ui.util.formatWon
import kotlinx.coroutines.launch

@Composable
fun RecurringRulesListScreen(
    repository: BudgetRepository,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rules by repository.observeRecurringRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = ScreenHorizontalPadding, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onBack,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recurring_back))
            }
            Text(
                stringResource(R.string.recurring_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            FilledTonalButton(onClick = onAdd, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.recurring_add))
            }
        }

        if (rules.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Autorenew,
                title = stringResource(R.string.recurring_empty_title),
                description = stringResource(R.string.recurring_empty_desc),
                actionLabel = stringResource(R.string.recurring_add),
                onAction = onAdd,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                contentPadding = PaddingValues(bottom = Space.xxxl),
            ) {
                items(rules, key = { r -> r.id }) { rule ->
                    val kind = CategoryKind.fromStorage(rule.kind)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (rule.enabled) 1f else 0.45f)
                            .clickable { onEdit(rule.id) },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(Space.lg),
                            horizontalArrangement = Arrangement.spacedBy(Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                                Text(
                                    rule.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.recurring_line_summary, rule.dayOfMonth),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = kindSignPrefix(kind) + rule.amountMinor.formatWon(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = kindAccent(kind),
                            )
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        repository.updateRecurringRule(rule.copy(enabled = checked))
                                    }
                                },
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
