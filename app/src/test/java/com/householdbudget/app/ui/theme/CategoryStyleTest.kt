package com.householdbudget.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.householdbudget.app.domain.category.FixedCategories
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryStyleTest {
    @Test fun everyFixedParent_hasItsOwnColorAndTreeEmoji() {
        for (p in FixedCategories.TREE) assertEquals(p.name, p.icon, categoryStyleOf(p.name).emoji)
        assertEquals(9, FixedCategories.TREE.map { categoryStyleOf(it.name).color }.toSet().size)
    }

    @Test fun palette_matchesSpec() {
        assertEquals(Color(0xFFFF8A65), categoryStyleOf("식비").color)
        assertEquals(Color(0xFF4FC3F7), categoryStyleOf("교통").color)
        assertEquals(Color(0xFFAED581), categoryStyleOf("생활").color)
        assertEquals(Color(0xFFBA68C8), categoryStyleOf("쇼핑").color)
        assertEquals(Color(0xFFFFD54F), categoryStyleOf("여가").color)
        assertEquals(Color(0xFF90A4AE), categoryStyleOf("기타").color)
        assertEquals(Color(0xFFF06292), categoryStyleOf("유동 지출").color)
        assertEquals(Color(0xFF64B5F6), categoryStyleOf("수입").color)
        assertEquals(Color(0xFF4DB6AC), categoryStyleOf("저축").color)
    }

    @Test fun unknownName_fallsBackToGrayAndFirstLetter() {
        assertEquals(CategoryStyle(Color(0xFF90A4AE), "가"), categoryStyleOf("가짜분류"))
        assertEquals("?", categoryStyleOf("").emoji)
    }

    @Test fun label_isEmojiThenName() = assertEquals("🍽️ 식비", categoryLabel("식비"))
}
