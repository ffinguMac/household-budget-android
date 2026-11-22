package com.householdbudget.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.ui.theme.Space
import com.householdbudget.app.ui.util.formatWon


@Composable
private fun MapCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.background,
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary.spentMinor.formatWon(),
                style = MaterialTheme.typography.displaySmall,
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
            BarPart.SPENT to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            BarPart.SAVINGS to MaterialTheme.colorScheme.tertiary,
            BarPart.LEFTOVER to MaterialTheme.colorScheme.surfaceVariant,
            BarPart.OVER to MaterialTheme.colorScheme.error,
        )
    Row(
        modifier =
            Modifier
                .padding(top = Space.xs)
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape),
    ) {
        segments.forEach { segment ->
            // 초과 구간은 카테고리색과 구분되게 2dp 틈을 둔다.
            if (segment.part == BarPart.OVER) Spacer(Modifier.width(2.dp).fillMaxHeight())
            Box(
                modifier =
                    Modifier
                        .weight(segment.weightMinor.toFloat())
                        .fillMaxHeight()
                        .background(colors.getValue(segment.part)),
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
                            .clip(CircleShape)
                            .background(colors.getValue(segment.part)),
                )
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
