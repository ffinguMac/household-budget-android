package com.householdbudget.app.ui.stats

import kotlin.math.abs
import kotlin.math.roundToInt

/** 전월 대비 %. 전월이 없거나 변화가 ±5% 이내(경계 포함)면 null(화면에서 "—"). */
internal fun deltaPercent(currentMinor: Long, previousMinor: Long): Int? {
    if (previousMinor <= 0) return null
    if (abs(currentMinor - previousMinor) * 100 <= 5 * previousMinor) return null
    return ((currentMinor - previousMinor) * 100.0 / previousMinor).roundToInt()
}

internal enum class BarPart { SPENT, SAVINGS, LEFTOVER, OVER }

internal data class BarSegment(val part: BarPart, val weightMinor: Long)

internal data class MapSummary(val incomeMinor: Long, val spentMinor: Long, val savingsMinor: Long) {
    val hasIncome: Boolean get() = incomeMinor > 0
    val spentPercent: Int get() = if (hasIncome) (spentMinor * 100.0 / incomeMinor).roundToInt() else 0
    val leftoverMinor: Long get() = incomeMinor - spentMinor - savingsMinor
    val isOver: Boolean get() = leftoverMinor < 0

    fun barSegments(): List<BarSegment> {
        if (!hasIncome) return emptyList()
        // 초과면 막대 전체 = 지출 + 저축, 끝쪽 초과분이 지출부터(넘치면 저축까지) 덮는다.
        val parts =
            if (isOver) {
                val over = -leftoverMinor
                listOf(
                    BarSegment(BarPart.SPENT, maxOf(spentMinor - over, 0)),
                    BarSegment(BarPart.SAVINGS, savingsMinor - maxOf(over - spentMinor, 0)),
                    BarSegment(BarPart.OVER, over),
                )
            } else {
                listOf(
                    BarSegment(BarPart.SPENT, spentMinor),
                    BarSegment(BarPart.SAVINGS, savingsMinor),
                    BarSegment(BarPart.LEFTOVER, leftoverMinor),
                )
            }
        return parts.filter { it.weightMinor > 0 }
    }
}

internal data class MapRow(
    val key: Long,
    val name: String,
    val amountMinor: Long,
    val sharePercent: Int,
    val deltaPercent: Int?,
    val barFraction: Float,
)

internal fun buildMapRows(parents: List<ParentSpend>): List<MapRow> {
    val spent = parents.filter { it.currentMinor > 0 }.sortedByDescending { it.currentMinor }
    val total = spent.sumOf { it.currentMinor }
    val max = spent.firstOrNull()?.currentMinor ?: return emptyList()
    return spent.map {
        MapRow(
            key = it.parentId,
            name = it.name,
            amountMinor = it.currentMinor,
            sharePercent = (it.currentMinor * 100.0 / total).roundToInt(),
            deltaPercent = deltaPercent(it.currentMinor, it.previousMinor),
            barFraction = it.currentMinor.toFloat() / max,
        )
    }
}

internal enum class TileTier { BIG, MID, SMALL }

/** 간격을 빼기 전 타일 크기 기준. */
internal fun tileTier(widthDp: Float, heightDp: Float): TileTier = when {
    widthDp >= 80f && heightDp >= 64f -> TileTier.BIG
    widthDp >= 54f && heightDp >= 40f -> TileTier.MID
    else -> TileTier.SMALL
}
