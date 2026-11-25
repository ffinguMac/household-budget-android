package com.householdbudget.app.domain.category

import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.category.CustomCategories.NameError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCategoriesTest {
    @Test fun validateName_acceptsTrimmedOneToTenChars() {
        assertNull(CustomCategories.validateName("  반려동물 ", listOf("통신")))
        assertNull(CustomCategories.validateName("가나다라마바사아자차", emptyList()))      // 10자
        assertNull(CustomCategories.validateName("🐶🐶🐶🐶🐶🐶🐶🐶🐶🐶", emptyList()))  // 코드포인트 10
    }

    @Test fun validateName_rejectsEmptyAndTooLong() {
        assertEquals(NameError.EMPTY, CustomCategories.validateName("   ", emptyList()))
        assertEquals(NameError.TOO_LONG, CustomCategories.validateName("가나다라마바사아자차카", emptyList())) // 11자
    }

    @Test fun validateName_duplicateIgnoresCaseAndSpaces() {
        assertEquals(NameError.DUPLICATE, CustomCategories.validateName("카 페", listOf("카페")))
        assertEquals(NameError.DUPLICATE, CustomCategories.validateName("GYM", listOf("gym")))
        assertNull(CustomCategories.validateName("카페2", listOf("카페")))
    }

    @Test fun deleteTargetName_prefersEtcThenDefaultThenFirst() {
        assertEquals("기타", CustomCategories.deleteTargetName(listOf("월급", "캐시백/이자", "용돈", "기타")))
        assertEquals("기본", CustomCategories.deleteTargetName(listOf("기본")))
        assertEquals("식사", CustomCategories.deleteTargetName(listOf("식사", "카페", "간식", "배달", "장보기")))
        assertNull(CustomCategories.deleteTargetName(emptyList()))
    }

    @Test fun deleteTargetName_existsForEveryFixedParent() {
        FixedCategories.TREE.forEach { assertNotNull(CustomCategories.deleteTargetName(it.children)) }
    }

    @Test fun isHidden_onlyWhenUsedMoreThan90DaysAgo() {
        assertFalse(CustomCategories.isHidden(null, 1000))      // 한 번도 안 씀
        assertFalse(CustomCategories.isHidden(910, 1000))       // 딱 90일
        assertTrue(CustomCategories.isHidden(909, 1000))        // 91일
        assertFalse(CustomCategories.isHidden(1005, 1000))      // 미래 날짜
    }

    @Test fun isFixedChild_matchesTreeExactly() {
        assertTrue(FixedCategories.isFixedChild(CategoryKind.EXPENSE, "식비", "카페"))
        assertFalse(FixedCategories.isFixedChild(CategoryKind.EXPENSE, "식비", "반려동물"))
        assertFalse(FixedCategories.isFixedChild(CategoryKind.INCOME, "식비", "카페"))
        assertEquals(listOf("기본"), FixedCategories.fixedChildrenOf(CategoryKind.EXPENSE, "유동 지출"))
    }

    @Test fun allowsCustomChildren_falseOnlyForSingleFixedChildParents() {
        assertFalse(FixedCategories.allowsCustomChildren(CategoryKind.EXPENSE, "유동 지출"))
        assertFalse(FixedCategories.allowsCustomChildren(CategoryKind.EXPENSE, "없는 대분류"))
        assertEquals(listOf("유동 지출"), FixedCategories.TREE.filterNot { FixedCategories.allowsCustomChildren(it.kind, it.name) }.map { it.name })
    }
}
