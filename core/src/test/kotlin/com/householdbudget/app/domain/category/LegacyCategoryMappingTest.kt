package com.householdbudget.app.domain.category

import com.householdbudget.app.domain.CategoryKind
import com.householdbudget.app.domain.CategoryKind.EXPENSE as E
import com.householdbudget.app.domain.CategoryKind.INCOME as I
import com.householdbudget.app.domain.CategoryKind.SAVINGS as S
import com.householdbudget.app.domain.category.LegacyCategoryMapping.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCategoryMappingTest {
    private fun t(k: CategoryKind, p: String, c: String, tag: Boolean) = LegacyTarget(CategoryPath(k, p, c), tag)

    private fun inTree(p: CategoryPath) =
        FixedCategories.TREE.any { it.kind == p.kind && it.name == p.parent && p.child in it.children }

    @Test fun specExamples() {
        assertEquals(t(E, "식비", "장보기", false), map(E, "생활/마트", "장보기"))
        assertEquals(t(E, "교통", "주유/주차", false), map(E, "차량", "주유"))
        assertEquals(t(E, "생활", "공과금", false), map(E, "주거/공과금", "수도"))
        assertEquals(t(E, "생활", "통신", false), map(E, "통신", "휴대폰"))
        assertEquals(t(E, "생활", "구독", false), map(E, "통신", "구독 서비스"))
        assertEquals(t(E, "기타", "의료", false), map(E, "의료", "약국"))
    }

    @Test fun unmapped_goesToOtherLeafWithTag() {
        assertEquals(t(E, "기타", "기타", true), map(E, "교육", "도서"))
        assertEquals(t(E, "기타", "기타", true), map(E, "취미생활", "레고"))
        assertEquals(t(I, "수입", "기타", true), map(I, "부수입", "중고 판매"))
        assertEquals(t(S, "저축", "기타", true), map(S, "비상금", "기본"))
    }

    @Test fun plainOther_isNotTagged() {
        assertEquals(t(E, "기타", "기타", false), map(E, "기타", "기본"))
        assertEquals(t(I, "수입", "기타", false), map(I, "기타 수입", "기본"))
    }

    @Test fun unknownChildUnderKnownParent_usesWildcard() {
        assertEquals(t(E, "식비", "식사", false), map(E, "식비", "야식"))
        assertEquals(t(E, "교통", "대중교통", false), map(E, "교통", "기본"))
    }

    @Test fun sameNameDifferentKind_doesNotCross() {
        assertEquals(t(I, "수입", "기타", true), map(I, "식비", "식사"))
    }

    @Test fun everyTarget_existsInFixedTree() {
        assertTrue(LegacyCategoryMapping.TABLE.isNotEmpty())
        for ((key, byChild) in LegacyCategoryMapping.TABLE) {
            for (path in byChild.values) {
                assertEquals(key.first, path.kind)
                assertTrue("not in tree: $path", inTree(path))
            }
            // 임의 문자열도 와일드카드로 트리 안에 떨어진다
            assertTrue(inTree(map(key.first, key.second, "임의-소분류").path))
        }
    }
}
