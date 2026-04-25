package com.householdbudget.app.ui.calendar

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.util.formatWon
import java.time.LocalDate
import java.time.ZoneId
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

    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    val monthlyIncome = totals.values.sumOf { it.incomeMinor }
    val monthlyExpense = totals.values.sumOf { it.expenseMinor }
    val monthlySavings = totals.values.sumOf { it.savingsMinor }

    val first = ym.atDay(1)
    // Sunday-first: SUN=7 → 7%7=0, MON=1 → 1%7=1, ..., SAT=6 → 6%7=6
    val offset = first.dayOfWeek.value % 7
    val daysInMonth = ym.lengthOfMonth()
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7
    val weekLabels = listOf("일", "월", "화", "수", "목", "금", "토")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // 월 네비게이션
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = DateTimeFormatter.ofPattern("yyyy년 M월").withLocale(Locale.KOREA).format(ym),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // 월별 수입/지출/저축 3열 통계
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalendarSummaryTile(
                    modifier = Modifier.weight(1f),
                    title = "월 수입",
                    amount = monthlyIncome.formatWon(),
                    color = MaterialTheme.colorScheme.secondary,
                )
                CalendarSummaryTile(
                    modifier = Modifier.weight(1f),
                    title = "월 지출",
                    amount = monthlyExpense.formatWon(),
                    color = MaterialTheme.colorScheme.error,
                )
                CalendarSummaryTile(
                    modifier = Modifier.weight(1f),
                    title = "월 저축",
                    amount = monthlySavings.formatWon(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // 달력 그리드
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
            ) {
                Column {
                    // 요일 헤더
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        weekLabels.forEach { w ->
                            Text(
                                text = w,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                    // 날짜 행
                    for (row in 0 until totalCells / 7) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 7) {
                                val i = row * 7 + col
                                val dayNum = i - offset + 1
                                val inMonth = dayNum in 1..daysInMonth
                                val isToday = inMonth && ym.atDay(dayNum) == today
                                val epoch = if (inMonth) ym.atDay(dayNum).toEpochDay() else -1L
                                val t = if (inMonth) totals[epoch] else null
                                val isSelected = inMonth && epoch == selectedEpochDay

                                CalendarCell(
                                    modifier = Modifier.weight(1f),
                                    dayNum = dayNum,
                                    inMonth = inMonth,
                                    isToday = isToday,
                                    isSelected = isSelected,
                                    hasIncome = (t?.incomeMinor ?: 0L) > 0L,
                                    hasExpense = (t?.expenseMinor ?: 0L) > 0L,
                                    hasSavings = (t?.savingsMinor ?: 0L) > 0L,
                                    onClick = {
                                        if (inMonth) {
                                            val tapped = ym.atDay(dayNum).toEpochDay()
                                            selectedEpochDay = if (selectedEpochDay == tapped) Long.MIN_VALUE else tapped
                                        }
                                    },
                                )
                            }
                        }
                        if (row < totalCells / 7 - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }

        // 선택된 날짜 거래 목록 (인라인)
        if (selectedEpochDay != Long.MIN_VALUE) {
            val selectedDay = LocalDate.ofEpochDay(selectedEpochDay)
            item(key = "day_detail") {
                DayDetailSection(
                    day = selectedDay,
                    repository = repository,
                )
            }
        }
    }
}

@Composable
private fun CalendarCell(
    dayNum: Int,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hasIncome: Boolean,
    hasExpense: Boolean,
    hasSavings: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(0.85f)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                    else -> Color.Transparent
                }
            )
            .then(if (inMonth) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(8.dp),
    ) {
        Text(
            text = if (inMonth) dayNum.toString() else "",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isToday -> MaterialTheme.colorScheme.primary
                inMonth -> MaterialTheme.colorScheme.onSurface
                else -> Color.Transparent
            },
            modifier = Modifier.align(Alignment.TopStart),
        )
        if (inMonth && (hasIncome || hasExpense || hasSavings)) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (hasIncome) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                }
                if (hasExpense) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
                if (hasSavings) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDetailSection(
    day: LocalDate,
    repository: BudgetRepository,
    modifier: Modifier = Modifier,
) {
    val epoch = day.toEpochDay()
    val txs by repository.observeTransactionsOnDay(epoch).collectAsStateWithLifecycle(initialValue = emptyList())
    val title = day.format(DateTimeFormatter.ofPattern("M월 d일 (E)").withLocale(Locale.KOREA))

    Column(modifier = modifier.fillMaxWidth()) {
        // 헤더
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(top = 24.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${txs.size}건",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }

        // 거래 목록
        if (txs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.calendar_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                txs.forEach { row ->
                    val kind = com.householdbudget.app.domain.CategoryKind.fromStorage(row.kind)
                    val amountColor = when (kind) {
                        com.householdbudget.app.domain.CategoryKind.INCOME -> MaterialTheme.colorScheme.secondary
                        com.householdbudget.app.domain.CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primary
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> MaterialTheme.colorScheme.error
                    }
                    val avatarBg = when (kind) {
                        com.householdbudget.app.domain.CategoryKind.INCOME -> MaterialTheme.colorScheme.secondaryContainer
                        com.householdbudget.app.domain.CategoryKind.SAVINGS -> MaterialTheme.colorScheme.primaryContainer
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val avatarText = when (kind) {
                        com.householdbudget.app.domain.CategoryKind.INCOME -> MaterialTheme.colorScheme.onSecondaryContainer
                        com.householdbudget.app.domain.CategoryKind.SAVINGS -> MaterialTheme.colorScheme.onPrimaryContainer
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val amountPrefix = when (kind) {
                        com.householdbudget.app.domain.CategoryKind.INCOME -> "+"
                        com.householdbudget.app.domain.CategoryKind.EXPENSE -> "−"
                        com.householdbudget.app.domain.CategoryKind.SAVINGS -> "↓"
                    }
                    val parentPrefix = row.parentCategoryName?.let { "$it · " }.orEmpty()

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(avatarBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = com.householdbudget.app.ui.util.resolveCategoryDisplay(
                                        leafIcon = row.categoryIcon,
                                        parentIcon = row.parentCategoryIcon,
                                        leafName = row.categoryName,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = avatarText,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "$parentPrefix${row.categoryName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (row.memo.isNotBlank()) {
                                    Text(
                                        text = row.memo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = amountPrefix + row.amountMinor.formatWon(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = amountColor,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CalendarSummaryTile(
    modifier: Modifier,
    title: String,
    amount: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
