package com.householdbudget.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.Locale

// ──────────────────────────────────────────────────────────────────────────
// 머니 맵: Finviz 스타일 squarified treemap.
// 색은 데이터 시각화 전용(전월 대비 증감 구간)이라 예외적으로 여기서만 하드코딩한다.
// ──────────────────────────────────────────────────────────────────────────

/** 트리맵 타일 하나. [key]는 탭 콜백 식별자 (null = 탭 무시 대상). */
data class MapTile(
    val label: String,
    val amountMinor: Long,
    val color: Color,
    val onColor: Color,
    val key: Long?,
)

/** [mergeSmallTiles] 결과: 최종 타일 목록 + "기타"로 합쳐진 원본 key 들. */
data class MergedTiles(
    val tiles: List<MapTile>,
    val mergedKeys: List<Long?>,
)

/**
 * 전체 대비 [minFraction] 미만인 타일을 하나의 "기타" 타일로 합친다.
 * 합칠 대상이 1개뿐이면 그대로 둔다 (이름만 바뀌는 무의미한 병합 방지).
 *
 * @param totalMinor 비율 계산 기준 총액. null 이면 [tiles] 합계.
 */
fun mergeSmallTiles(
    tiles: List<MapTile>,
    otherLabel: String,
    otherColor: Color,
    otherOnColor: Color,
    otherKey: Long?,
    totalMinor: Long? = null,
    minFraction: Double = 0.015,
): MergedTiles {
    val total = (totalMinor ?: tiles.sumOf { it.amountMinor }).coerceAtLeast(1L)
    val (small, big) = tiles.partition { it.amountMinor.toDouble() / total < minFraction }
    if (small.size < 2) return MergedTiles(tiles = tiles, mergedKeys = emptyList())
    val merged =
        MapTile(
            label = otherLabel,
            amountMinor = small.sumOf { it.amountMinor },
            color = otherColor,
            onColor = otherOnColor,
            key = otherKey,
        )
    return MergedTiles(tiles = big + merged, mergedKeys = small.map { it.key })
}

private data class PlacedTile(val tile: MapTile, val rect: Rect)

private data class RenderTile(
    val rect: Rect,
    val color: Color,
    val onColor: Color,
    val labelLayout: TextLayoutResult?,
    val amountLayout: TextLayoutResult?,
)

/**
 * squarified treemap 캔버스. 값이 큰 타일부터 배치하며 각 행의 worst aspect ratio 를
 * 최소화한다 (Bruls et al.). amountMinor <= 0 인 타일은 그리지 않는다.
 */
@Composable
fun MoneyMap(
    tiles: List<MapTile>,
    onTileClick: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle =
        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val amountStyle = MaterialTheme.typography.labelSmall

    // 배치 + 텍스트 측정은 (타일, 크기) 가 바뀔 때만 다시 계산한다 — 리컴포지션마다
    // squarify/measure 를 반복하지 않는 것이 이 화면 성능의 핵심.
    val placements =
        remember(tiles, canvasSize) {
            if (canvasSize.width > 0 && canvasSize.height > 0) {
                squarifiedLayout(
                    tiles = tiles,
                    width = canvasSize.width.toFloat(),
                    height = canvasSize.height.toFloat(),
                )
            } else {
                emptyList()
            }
        }
    val renderTiles =
        remember(placements, textMeasurer, labelStyle, amountStyle, density) {
            with(density) {
                val gap = 3.dp.toPx()
                val minLabelW = 64.dp.toPx()
                val minLabelH = 40.dp.toPx()
                val pad = 6.dp.toPx()
                placements.map { placed ->
                    val inner =
                        if (placed.rect.width > gap && placed.rect.height > gap) {
                            placed.rect.deflate(gap / 2f)
                        } else {
                            placed.rect
                        }
                    val showLabel = inner.width >= minLabelW && inner.height >= minLabelH
                    val maxTextWidth = (inner.width - pad * 2).toInt().coerceAtLeast(0)
                    RenderTile(
                        rect = inner,
                        color = placed.tile.color,
                        onColor = placed.tile.onColor,
                        labelLayout =
                            if (showLabel && maxTextWidth > 0) {
                                measureSingleLine(
                                    textMeasurer,
                                    placed.tile.label,
                                    labelStyle,
                                    maxTextWidth,
                                )
                            } else {
                                null
                            },
                        amountLayout =
                            if (showLabel && maxTextWidth > 0) {
                                measureSingleLine(
                                    textMeasurer,
                                    compactAmountLabel(placed.tile.amountMinor),
                                    amountStyle,
                                    maxTextWidth,
                                )
                            } else {
                                null
                            },
                    )
                }
            }
        }
    val mapDescription =
        remember(tiles) {
            tiles
                .filter { it.amountMinor > 0 }
                .sortedByDescending { it.amountMinor }
                .joinToString(", ") { "${it.label} ${compactAmountLabel(it.amountMinor)}" }
        }

    Canvas(
        modifier =
            modifier
                .onSizeChanged { canvasSize = it }
                .semantics { contentDescription = mapDescription }
                .pointerInput(placements) {
                    detectTapGestures { position ->
                        placements
                            .firstOrNull { it.rect.contains(position) }
                            ?.let { onTileClick(it.tile.key) }
                    }
                },
    ) {
        val corner = CornerRadius(4.dp.toPx())
        val pad = 6.dp.toPx()
        renderTiles.forEach { tile ->
            drawRoundRect(
                color = tile.color,
                topLeft = Offset(tile.rect.left, tile.rect.top),
                size = Size(tile.rect.width, tile.rect.height),
                cornerRadius = corner,
            )
            val label = tile.labelLayout
            if (label != null) {
                drawText(
                    textLayoutResult = label,
                    color = tile.onColor,
                    topLeft = Offset(tile.rect.left + pad, tile.rect.top + pad),
                )
                val amount = tile.amountLayout
                if (amount != null &&
                    pad + label.size.height + amount.size.height <= tile.rect.height
                ) {
                    drawText(
                        textLayoutResult = amount,
                        color = tile.onColor.copy(alpha = 0.8f),
                        topLeft =
                            Offset(
                                tile.rect.left + pad,
                                tile.rect.top + pad + label.size.height,
                            ),
                    )
                }
            }
        }
    }
}

private fun measureSingleLine(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
): TextLayoutResult =
    measurer.measure(
        text = AnnotatedString(text),
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
    tiles: List<MapTile>,
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
