package com.householdbudget.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.householdbudget.app.R
import com.householdbudget.app.ui.theme.categoryStyleOf
import java.util.Locale

// ──────────────────────────────────────────────────────────────────────────
// 머니 맵: squarified treemap. 타일 색은 카테고리 색(categoryStyleOf),
// 면적은 지출 금액. 전월 대비는 큰 타일에 글자(▲/▼)로만 보여준다.
// ──────────────────────────────────────────────────────────────────────────

private data class PlacedTile(val tile: MapRow, val rect: Rect)

private class RenderTile(
    /** 간격만큼 줄인 사각형. */
    val rect: Rect,
    val color: Color,
    /** 위에서부터 쌓을 줄들(타일 높이를 넘는 줄은 이미 뺐다). SMALL 타일은 가운데 놓을 이모지 하나. */
    val lines: List<TextLayoutResult>,
    val centered: Boolean,
)

/**
 * 지출 대분류 트리맵. [rows]는 [buildMapRows] 결과(금액 > 0, 내림차순).
 * 타일 크기(dp)에 따라 [tileTier]로 글자 양을 정한다.
 */
@Composable
internal fun MoneyMap(
    rows: List<MapRow>,
    onTileClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val deltas = rows.map { deltaDisplay(it.deltaPercent) }
    val descriptions =
        rows.map { row ->
            val amount = compactAmountLabel(row.amountMinor)
            val pct = row.deltaPercent
            when {
                pct == null -> stringResource(R.string.stats_map_tile_desc_flat, row.name, amount)
                pct > 0 -> stringResource(R.string.stats_map_tile_desc_up, row.name, amount, pct)
                else -> stringResource(R.string.stats_map_tile_desc_down, row.name, amount, -pct)
            }
        }

    // 배치 + 텍스트 측정은 (행, 크기)가 바뀔 때만 다시 계산한다 — 리컴포지션마다
    // squarify/measure 를 반복하지 않는 것이 이 화면 성능의 핵심.
    val placements =
        remember(rows, canvasSize) {
            if (canvasSize.width > 0 && canvasSize.height > 0) {
                squarifiedLayout(
                    tiles = rows,
                    width = canvasSize.width.toFloat(),
                    height = canvasSize.height.toFloat(),
                )
            } else {
                emptyList()
            }
        }
    val renderTiles =
        remember(placements, deltas, textMeasurer, typography, onSurface, onSurfaceVariant, density) {
            // 줄마다 따로 재는 한 줄 문단이라 기본 Trim.Both에선 lineHeight가 무시된다 → Trim.None으로
            // 줄 높이를 글자 크기의 1.15배로 고정한다. 최소 BIG(80×64dp)엔 이모지·금액, 최소 MID(54×40dp)엔 금액만 들어간다.
            fun TextStyle.tile() =
                copy(
                    color = onSurface,
                    lineHeight = fontSize * 1.15f,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                )
            val bigEmoji = typography.titleMedium.tile()
            val bigName = typography.bodySmall.tile()
            val bigAmount = typography.titleMedium.copy(fontWeight = FontWeight.Bold).tile()
            val bigMeta = typography.labelSmall.tile()
            val midEmoji = typography.labelMedium.tile()
            val midName = typography.labelSmall.tile()
            val midAmount = typography.bodySmall.copy(fontWeight = FontWeight.Bold).tile()
            with(density) {
                val inset = 3.dp.toPx()
                val pad = 8.dp.toPx()
                placements.mapIndexed { index, placed ->
                    val row = placed.tile
                    val style = categoryStyleOf(row.name)
                    val inner =
                        if (placed.rect.width > inset * 2 && placed.rect.height > inset * 2) {
                            placed.rect.deflate(inset)
                        } else {
                            placed.rect
                        }
                    val maxTextWidth = (inner.width - pad * 2).toInt().coerceAtLeast(0)
                    fun line(text: AnnotatedString, textStyle: TextStyle) =
                        measureSingleLine(textMeasurer, text, textStyle, maxTextWidth)
                    val tier = tileTier(placed.rect.width.toDp().value, placed.rect.height.toDp().value)
                    if (tier == TileTier.SMALL || maxTextWidth == 0) {
                        val emoji =
                            measureSingleLine(
                                textMeasurer,
                                AnnotatedString(style.emoji),
                                midEmoji,
                                inner.width.toInt(),
                            )
                        val fits = minOf(inner.width, inner.height) >=
                            maxOf(emoji.size.width, emoji.size.height)
                        RenderTile(inner, style.color, if (fits) listOf(emoji) else emptyList(), true)
                    } else {
                        val big = tier == TileTier.BIG
                        val amount = AnnotatedString(compactAmountLabel(row.amountMinor))
                        val layouts = buildList {
                            add(line(AnnotatedString(style.emoji), if (big) bigEmoji else midEmoji))
                            add(line(AnnotatedString(row.name), if (big) bigName else midName))
                            add(line(amount, if (big) bigAmount else midAmount))
                            if (big) {
                                val (deltaText, deltaTint) = deltas[index]
                                val meta = buildAnnotatedString {
                                    withStyle(SpanStyle(color = onSurfaceVariant)) {
                                        append("${row.sharePercent}% ")
                                    }
                                    withStyle(SpanStyle(color = deltaTint)) { append(deltaText) }
                                }
                                add(line(meta, bigMeta))
                            }
                        }
                        // 높이가 모자라면 우선순위가 낮은 줄부터 뺀다.
                        // BIG: 이모지 > 금액 > 이름 > 비율·증감, MID: 금액 > 이모지 > 이름.
                        var used = pad
                        val kept =
                            (if (big) listOf(0, 2, 1, 3) else listOf(2, 0, 1))
                                .takeWhile { i ->
                                    val h = layouts[i].size.height
                                    (used + h <= inner.height).also { if (it) used += h }
                                }
                        val fitted = layouts.filterIndexed { i, _ -> i in kept }
                        RenderTile(inner, style.color, fitted, false)
                    }
                }
            }
        }
    val mapDescription =
        remember(rows, descriptions) {
            rows.indices.sortedByDescending { rows[it].amountMinor }.joinToString(", ") { descriptions[it] }
        }

    val onClick by rememberUpdatedState(onTileClick)
    Canvas(
        modifier =
            modifier
                .onSizeChanged { canvasSize = it }
                .semantics { contentDescription = mapDescription }
                .pointerInput(placements) {
                    detectTapGestures { position ->
                        placements
                            .firstOrNull { it.rect.contains(position) }
                            ?.let { onClick(it.tile.key) }
                    }
                },
    ) {
        val corner = CornerRadius(14.dp.toPx())
        val strokeWidth = 1.5.dp.toPx()
        val pad = 8.dp.toPx()
        renderTiles.forEach { tile ->
            val topLeft = Offset(tile.rect.left, tile.rect.top)
            val size = Size(tile.rect.width, tile.rect.height)
            drawRoundRect(tile.color.copy(alpha = 0.15f), topLeft, size, corner)
            // 테두리는 사각형 안쪽에 그려지도록 선 두께 절반만큼 들인다.
            val half = strokeWidth / 2f
            drawRoundRect(
                color = tile.color.copy(alpha = 0.5f),
                topLeft = Offset(topLeft.x + half, topLeft.y + half),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = CornerRadius(corner.x - half),
                style = Stroke(strokeWidth),
            )
            if (tile.centered) {
                tile.lines.firstOrNull()?.let { emoji ->
                    drawText(
                        textLayoutResult = emoji,
                        topLeft =
                            Offset(
                                tile.rect.center.x - emoji.size.width / 2f,
                                tile.rect.center.y - emoji.size.height / 2f,
                            ),
                    )
                }
            } else {
                var y = tile.rect.top + pad
                tile.lines.forEach { layout ->
                    drawText(textLayoutResult = layout, topLeft = Offset(tile.rect.left + pad, y))
                    y += layout.size.height
                }
            }
        }
    }
}

private fun measureSingleLine(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    maxWidthPx: Int,
): TextLayoutResult =
    measurer.measure(
        text = text,
        style = style,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidthPx),
    )

/** 타일 안에 넣는 짧은 금액 라벨: 12,345원 / 1.2만 / 350만 / 1.5억 */
internal fun compactAmountLabel(minor: Long): String =
    when {
        minor >= 100_000_000L ->
            String.format(Locale.KOREA, "%.1f억", minor / 100_000_000.0)
                .replace(".0억", "억")
        minor >= 1_000_000L ->
            String.format(Locale.KOREA, "%.0f만", minor / 10_000.0)
        minor >= 10_000L ->
            String.format(Locale.KOREA, "%.1f만", minor / 10_000.0)
                .replace(".0만", "만")
        else -> String.format(Locale.KOREA, "%,d원", minor)
    }

/**
 * squarified treemap 배치. 재귀 없이 루프로 진행:
 * 값 내림차순으로 정렬한 뒤, 남은 사각형의 짧은 변을 따라 행을 만들고
 * 행의 worst aspect ratio 가 나빠지기 직전까지 타일을 채워 넣는다.
 */
private fun squarifiedLayout(
    tiles: List<MapRow>,
    width: Float,
    height: Float,
): List<PlacedTile> {
    val sorted = tiles.filter { it.amountMinor > 0 }.sortedByDescending { it.amountMinor }
    val total = sorted.sumOf { it.amountMinor }.toDouble()
    if (sorted.isEmpty() || total <= 0.0 || width <= 0f || height <= 0f) return emptyList()

    val scale = width.toDouble() * height.toDouble() / total
    val areas = sorted.map { it.amountMinor * scale }
    val result = ArrayList<PlacedTile>(sorted.size)

    var x = 0.0
    var y = 0.0
    var w = width.toDouble()
    var h = height.toDouble()
    var start = 0
    while (start < areas.size && w > 0.0 && h > 0.0) {
        val shortSide = minOf(w, h)
        var end = start + 1
        var rowSum = areas[start]
        var worst = worstAspect(areas, start, end, rowSum, shortSide)
        while (end < areas.size) {
            val nextSum = rowSum + areas[end]
            val nextWorst = worstAspect(areas, start, end + 1, nextSum, shortSide)
            if (nextWorst > worst) break
            rowSum = nextSum
            worst = nextWorst
            end++
        }

        val thickness = if (shortSide > 0.0) rowSum / shortSide else 0.0
        if (w >= h) {
            // 짧은 변 = 세로 → 왼쪽에 세로 스트립을 놓고 위→아래로 채운다.
            var cy = y
            for (i in start until end) {
                val len = if (thickness > 0.0) areas[i] / thickness else 0.0
                result +=
                    PlacedTile(
                        tile = sorted[i],
                        rect =
                            Rect(
                                x.toFloat(),
                                cy.toFloat(),
                                (x + thickness).toFloat(),
                                (cy + len).toFloat(),
                            ),
                    )
                cy += len
            }
            x += thickness
            w = (w - thickness).coerceAtLeast(0.0)
        } else {
            // 짧은 변 = 가로 → 위쪽에 가로 스트립을 놓고 왼쪽→오른쪽으로 채운다.
            var cx = x
            for (i in start until end) {
                val len = if (thickness > 0.0) areas[i] / thickness else 0.0
                result +=
                    PlacedTile(
                        tile = sorted[i],
                        rect =
                            Rect(
                                cx.toFloat(),
                                y.toFloat(),
                                (cx + len).toFloat(),
                                (y + thickness).toFloat(),
                            ),
                    )
                cx += len
            }
            y += thickness
            h = (h - thickness).coerceAtLeast(0.0)
        }
        start = end
    }
    return result
}

/** 행 [from, toExclusive) 의 worst aspect ratio. [side] = 행이 붙는 짧은 변 길이. */
private fun worstAspect(
    areas: List<Double>,
    from: Int,
    toExclusive: Int,
    rowSum: Double,
    side: Double,
): Double {
    var maxArea = 0.0
    var minArea = Double.MAX_VALUE
    for (i in from until toExclusive) {
        if (areas[i] > maxArea) maxArea = areas[i]
        if (areas[i] < minArea) minArea = areas[i]
    }
    if (rowSum <= 0.0 || side <= 0.0 || minArea <= 0.0) return Double.MAX_VALUE
    val sum2 = rowSum * rowSum
    val side2 = side * side
    return maxOf(side2 * maxArea / sum2, sum2 / (side2 * minArea))
}
