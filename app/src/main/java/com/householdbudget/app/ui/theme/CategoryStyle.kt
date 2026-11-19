package com.householdbudget.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.householdbudget.app.domain.category.FixedCategories

// 카테고리 색은 데이터 구분용 고정값이라 테마 색이 아니다.
data class CategoryStyle(val color: Color, val emoji: String)

private val FALLBACK_COLOR = Color(0xFF90A4AE)

private val COLORS: Map<String, Color> =
    mapOf(
        "식비" to Color(0xFFFF8A65),
        "교통" to Color(0xFF4FC3F7),
        "생활" to Color(0xFFAED581),
        "쇼핑" to Color(0xFFBA68C8),
        "여가" to Color(0xFFFFD54F),
        "기타" to FALLBACK_COLOR,
        "유동 지출" to Color(0xFFF06292),
        "수입" to Color(0xFF64B5F6),
        "저축" to Color(0xFF4DB6AC),
    )

private val EMOJIS: Map<String, String> = FixedCategories.TREE.associate { it.name to it.icon }

fun categoryStyleOf(parentName: String): CategoryStyle =
    CategoryStyle(
        color = COLORS[parentName] ?: FALLBACK_COLOR,
        emoji = EMOJIS[parentName] ?: parentName.take(1).ifEmpty { "?" },
    )

fun categoryLabel(parentName: String): String = "${categoryStyleOf(parentName).emoji} $parentName"

@Composable
fun CategoryDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}
