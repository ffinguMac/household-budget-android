package com.householdbudget.app.ui.components

import com.householdbudget.app.data.local.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPickerLogicTest {
    private fun cat(id: Long, name: String, parentId: Long? = null) =
        CategoryEntity(id = id, name = name, kind = "EXPENSE", parentId = parentId, sortOrder = id.toInt())

    @Test fun visibleLeaves_dropsHiddenButKeepsSelected() {
        val leaves = listOf(cat(1, "통신", 9), cat(2, "오래됨", 9), cat(3, "옛날", 9))
        assertEquals(listOf(1L), visibleLeaves(leaves, setOf(2, 3), null).map { it.id })
        assertEquals(listOf(1L, 3L), visibleLeaves(leaves, setOf(2, 3), 3).map { it.id })
        assertEquals(leaves, visibleLeaves(leaves, emptySet(), null))
    }

    @Test fun isCustomLeaf_falseForFixedNames() {
        val food = cat(9, "식비")
        assertFalse(isCustomLeaf(food, cat(1, "카페", 9)))
        assertTrue(isCustomLeaf(food, cat(2, "반려동물", 9)))
    }
}
