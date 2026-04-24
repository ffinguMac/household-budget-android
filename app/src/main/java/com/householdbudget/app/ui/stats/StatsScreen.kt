package com.householdbudget.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.R
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.data.repository.MonthlyTotal
import com.householdbudget.app.data.repository.ParentSpend
import com.householdbudget.app.ui.components.ScreenHorizontalPadding
import com.householdbudget.app.ui.util.formatWon
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(
    repository: BudgetRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: StatsViewModel = viewModel(factory = StatsViewModelFactory(repository))
    val parentSpend by vm.parentSpend.collectAsStateWithLifecycle()
    val monthlyTotals by vm.monthlyTotals.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ── 도넛: 대분류별 지출 ───────────────────────────────────────────
        Text(
            text = stringResource(R.string.stats_monthly_breakdown),
            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (parentSpend.isEmpty() || parentSpend.sumOf { it.amountMinor } <= 0L) {
                    Text(
                        stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DonutChart(data = parentSpend.take(6))
                    Spacer(Modifier.height(12.dp))
                    LegendList(data = parentSpend.take(6), total = parentSpend.sumOf { it.amountMinor })
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 라인: 최근 6개월 지출/저축 추이 ──────────────────────────────
        Text(
            text = stringResource(R.string.stats_trend_6m),
            modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenHorizontalPadding),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (monthlyTotals.isEmpty()) {
                    Text(
                        stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LineChart(data = monthlyTotals)
                }
            }
        }
    }
}

/** 팔레트: 대분류별로 순환 사용. */
private val palette = listOf(
    Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
    Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFF9575CD), Color(0xFFF06292),
)

@Composable
private fun DonutChart(data: List<ParentSpend>) {
    val total = data.sumOf { it.amountMinor }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            if (total <= 0L) return@Canvas
            var startAngle = -90f
            val strokeWidth = 40f
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            data.forEachIndexed { index, p ->
                val sweep = (p.amountMinor.toFloat() / total.toFloat()) * 360f
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                startAngle += sweep
            }
        }
        Text(
            text = total.formatWon(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LegendList(data: List<ParentSpend>, total: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.forEachIndexed { index, p ->
            val pct = if (total > 0) ((p.amountMinor * 100) / total).toInt() else 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(palette[index % palette.size]),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = p.parentName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${p.amountMinor.formatWon()} (${pct}%)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LineChart(data: List<MonthlyTotal>) {
    val expenseColor = MaterialTheme.colorScheme.error
    val savingsColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val labelFmt = DateTimeFormatter.ofPattern("M월").withLocale(Locale.KOREA)

    val maxY = data.maxOfOrNull { maxOf(it.expenseMinor, it.savingsMinor) }?.toFloat() ?: 0f

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            if (maxY <= 0f || data.size < 2) return@Canvas
            val padX = 16f
            val padY = 16f
            val plotW = size.width - padX * 2
            val plotH = size.height - padY * 2
            val stepX = plotW / (data.size - 1)

            // 가로 그리드 라인 4개
            for (i in 0..4) {
                val y = padY + (plotH / 4f) * i
                drawLine(
                    color = gridColor,
                    start = Offset(padX, y),
                    end = Offset(padX + plotW, y),
                    strokeWidth = 1f,
                )
            }

            fun buildPath(selector: (MonthlyTotal) -> Long): Path {
                val p = Path()
                data.forEachIndexed { i, m ->
                    val x = padX + stepX * i
                    val frac = selector(m).toFloat() / maxY
                    val y = padY + plotH - plotH * frac
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                return p
            }

            drawPath(
                path = buildPath { it.expenseMinor },
                color = expenseColor,
                style = Stroke(width = 4f),
            )
            drawPath(
                path = buildPath { it.savingsMinor },
                color = savingsColor,
                style = Stroke(width = 4f),
            )

            // 각 데이터 포인트
            data.forEachIndexed { i, m ->
                val x = padX + stepX * i
                val fracE = m.expenseMinor.toFloat() / maxY
                val fracS = m.savingsMinor.toFloat() / maxY
                drawCircle(expenseColor, radius = 4f, center = Offset(x, padY + plotH - plotH * fracE))
                drawCircle(savingsColor, radius = 4f, center = Offset(x, padY + plotH - plotH * fracS))
            }
        }
        // X축 라벨
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            data.forEach { m ->
                Text(
                    text = labelFmt.format(m.yearMonth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 범례
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendSwatch(color = expenseColor, label = stringResource(R.string.home_expense))
            LegendSwatch(color = savingsColor, label = stringResource(R.string.home_savings))
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
