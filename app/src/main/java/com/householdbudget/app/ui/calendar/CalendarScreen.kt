package com.householdbudget.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    repository: BudgetRepository,
    modifier: Modifier = Modifier,
) {
    val ym by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val totals by viewModel.dayTotals.collectAsStateWithLifecycle()
    var selectedEpochDay by remember { mutableLongStateOf(Long.MIN_VALUE) }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = { viewModel.previousMonth() },
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                }
                Text(
                    text =
                        DateTimeFormatter.ofPattern("yyyy년 M월")
                            .withLocale(Locale.KOREA)
                            .format(ym),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = { viewModel.nextMonth() },
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        ) {
            val weekLabels = listOf("월", "화", "수", "목", "금", "토", "일")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                weekLabels.forEach { w ->
                    Text(
                        text = w,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        val first = ym.atDay(1)
        val offset = (first.dayOfWeek.value + 6) % 7
        val daysInMonth = ym.lengthOfMonth()
        val totalCells = ((offset + daysInMonth + 6) / 7) * 7

        Column(Modifier.fillMaxWidth()) {
            for (row in 0 until totalCells / 7) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (col in 0 until 7) {
                        val i = row * 7 + col
                        val dayNum = i - offset + 1
                        val inMonth = dayNum in 1..daysInMonth
                        Surface(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .then(
                                        if (inMonth) {
                                            Modifier.clickable {
                                                val d = ym.atDay(dayNum)
                                                selectedEpochDay = d.toEpochDay()
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            shape = MaterialTheme.shapes.medium,
                            color =
                                if (inMonth) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                            tonalElevation = if (inMonth) 1.dp else 0.dp,
                            shadowElevation = if (inMonth) 1.dp else 0.dp,
                        ) {
                            if (inMonth) {
                                val epoch = ym.atDay(dayNum).toEpochDay()
                                val t = totals[epoch]
                                CalendarDayCellContent(
                                    dayNum = dayNum,
                                    incomeMinor = t?.incomeMinor ?: 0L,
                                    expenseMinor = t?.expenseMinor ?: 0L,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedEpochDay != Long.MIN_VALUE) {
        val day = LocalDate.ofEpochDay(selectedEpochDay)
        DayDetailDialog(
            day = day,
            repository = repository,
            onDismiss = { selectedEpochDay = Long.MIN_VALUE },
        )
    }
}

@Composable
private fun CalendarDayCellContent(
    dayNum: Int,
    incomeMinor: Long,
    expenseMinor: Long,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = dayNum.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (incomeMinor > 0) {
            Text(
                text = "+${formatCompactWon(incomeMinor)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expenseMinor > 0) {
            Text(
                text = "-${formatCompactWon(expenseMinor)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (incomeMinor == 0L && expenseMinor == 0L) {
            Text(
                text = "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCompactWon(minor: Long): String {
    val s = minor.formatWon()
    return s.replace("원", "").trim()
}

@Composable
private fun DayDetailDialog(
    day: LocalDate,
    repository: BudgetRepository,
    onDismiss: () -> Unit,
) {
    val epoch = day.toEpochDay()
    val txs by repository.observeTransactionsOnDay(epoch).collectAsStateWithLifecycle(initialValue = emptyList())
    val title =
        day.format(DateTimeFormatter.ofPattern("yyyy.MM.dd (E)").withLocale(Locale.KOREA))

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            if (txs.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_day_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                ) {
                    items(txs, key = { it.id }) { row ->
                        val sign = if (row.isIncome) "+" else "-"
                        Text(
                            text =
                                "${row.categoryName} · $sign${row.amountMinor.formatWon()}" +
                                    if (row.memo.isNotBlank()) "\n${row.memo}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        HorizontalDivider(
                            Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.calendar_day_close))
            }
        },
    )
}
