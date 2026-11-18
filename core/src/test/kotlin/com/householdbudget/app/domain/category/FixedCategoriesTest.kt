package com.householdbudget.app.domain.category

import com.householdbudget.app.domain.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedCategoriesTest {
    @Test fun tree_hasNineParentsAndThirtyOneLeaves() {
        assertEquals(9, FixedCategories.TREE.size)
        assertEquals(31, FixedCategories.TREE.sumOf { it.children.size })
    }

    @Test fun floatingExpense_hasSingleDefaultLeaf() {
        val p = FixedCategories.TREE.single { it.name == "유동 지출" }
        assertEquals(CategoryKind.EXPENSE, p.kind)
        assertEquals(listOf("기본"), p.children)
    }

    @Test fun otherLeaf_existsInTreeForEveryKind() {
        for (k in CategoryKind.entries) {
            val o = FixedCategories.otherLeaf(k)
            assertTrue(FixedCategories.TREE.any { it.kind == k && it.name == o.parent && o.child in it.children })
        }
    }
}
