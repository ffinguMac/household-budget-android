package com.householdbudget.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.ui.theme.CategoryDot
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.theme.categoryLabel
import com.householdbudget.app.ui.theme.categoryStyleOf
import com.householdbudget.app.ui.util.formatWon

private const val RANK_MAX_ROWS = 7

// 막대 "지출" 색은 식비 색과 같은 데이터 시각화 고정값.
private val SpentBarColor = Color(0xFFFF8A65)

@Composable
private fun MapCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        content = content,
    )
}

/** 맵 상단 요약: 이번 달 지출 합계 + (수입이 있으면) 수입 대비 지출/저축/남은 돈 막대. */
@Composable
internal fun MapSummaryCard(
    month: Int,
    summary: MapSummary,
    modifier: Modifier = Modifier,
) {
    MapCard(modifier) {
        Column(
            modifier = Modifier.padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = stringResource(R.string.stats_map_summary_title, month),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary.spentMinor.formatWon(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summary.hasIncome) {
                SummaryLine(summary)
                SummaryBar(summary)
            }
        }
    }
}

@Composable
private fun SummaryLine(summary: MapSummary) {
    val income =
        stringResource(
            R.string.stats_map_summary_income,
            compactAmountLabel(summary.incomeMinor),
            summary.spentPercent,
        )
    val savings =
        stringResource(R.string.stats_map_summary_savings, compactAmountLabel(summary.savingsMinor))
    val last =
        if (summary.isOver) {
            stringResource(R.string.stats_map_summary_over, compactAmountLabel(-summary.leftoverMinor))
        } else {
            stringResource(R.string.stats_map_summary_leftover, compactAmountLabel(summary.leftoverMinor))
        }
    val error = MaterialTheme.colorScheme.error
    Text(
        text =
            buildAnnotatedString {
                append("$income · $savings · ")
                if (summary.isOver) withStyle(SpanStyle(color = error)) { append(last) } else append(last)
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryBar(summary: MapSummary) {
    val segments = summary.barSegments()
    val colors =
        mapOf(
            BarPart.SPENT to SpentBarColor,
            BarPart.SAVINGS to categoryStyleOf("저축").color,
            BarPart.LEFTOVER to MaterialTheme.colorScheme.surfaceContainerHighest,
            BarPart.OVER to MaterialTheme.colorScheme.error,
        )
    val over = segments.firstOrNull { it.part == BarPart.OVER }
    Box(
        modifier =
            Modifier
                .padding(top = Space.xs)
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
    ) {
        // 초과일 때 막대 전체 = 지출 + 저축, 끝쪽 빨간 구간 = 수입을 넘은 금액.
        Row(modifier = Modifier.fillMaxSize()) {
            segments.filter { it.part != BarPart.OVER }.forEach { segment ->
                Box(
                    modifier =
                        Modifier
                            .weight(segment.weightMinor.toFloat())
                            .fillMaxHeight()
                            .background(colors.getValue(segment.part)),
                )
            }
        }
        if (over != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(
                            over.weightMinor.toFloat() / (summary.spentMinor + summary.savingsMinor),
                        )
                        .background(colors.getValue(BarPart.OVER)),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        segments.forEach { segment ->
            val label =
                when (segment.part) {
                    BarPart.SPENT -> R.string.stats_map_legend_spent
                    BarPart.SAVINGS -> R.string.stats_map_savings
                    BarPart.LEFTOVER -> R.string.stats_map_leftover
                    BarPart.OVER -> R.string.stats_map_overspent
                }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.getValue(segment.part)),
                )
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 지출 대분류 순위(최대 [RANK_MAX_ROWS]행). 행을 누르면 그 대분류 거래 목록. */
@Composable
internal fun MapRankList(
    rows: List<MapRow>,
    onRowClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    MapCard(modifier) {
        Column(modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.sm)) {
            rows.take(RANK_MAX_ROWS).forEach { row -> RankRow(row, onClick = { onRowClick(row.key) }) }
        }
    }
}

@Composable
private fun RankRow(row: MapRow, onClick: () -> Unit) {
    val color = categoryStyleOf(row.name).color
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        CategoryDot(color)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                text = categoryLabel(row.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(row.barFraction)
                            .fillMaxHeight()
                            .background(color),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = compactAmountLabel(row.amountMinor),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            DeltaLabel(row.deltaPercent)
        }
    }
}
