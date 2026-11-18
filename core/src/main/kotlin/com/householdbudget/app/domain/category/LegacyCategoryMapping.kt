package com.householdbudget.app.domain.category

import com.householdbudget.app.domain.CategoryKind

data class LegacyTarget(val path: CategoryPath, val tagMemo: Boolean)

object LegacyCategoryMapping {
    private const val ANY = "*"
    private val E = CategoryKind.EXPENSE
    private val I = CategoryKind.INCOME
    private val S = CategoryKind.SAVINGS

    private fun e(parent: String, child: String) = CategoryPath(E, parent, child)
    private fun i(child: String) = CategoryPath(I, "수입", child)
    private fun s(child: String) = CategoryPath(S, "저축", child)

    // (kind, 옛 대분류) -> (옛 소분류 or "*") -> 새 경로
    internal val TABLE: Map<Pair<CategoryKind, String>, Map<String, CategoryPath>> = mapOf(
        (E to "식비") to mapOf(
            "식사" to e("식비", "식사"), "카페" to e("식비", "카페"), "간식" to e("식비", "간식"),
            "배달" to e("식비", "배달"), "술/모임" to e("여가", "술/모임"), ANY to e("식비", "식사"),
        ),
        (E to "생활/마트") to mapOf("장보기" to e("식비", "장보기"), ANY to e("생활", "생활용품")),
        (E to "주거/공과금") to mapOf(ANY to e("생활", "공과금")),
        (E to "통신") to mapOf("구독 서비스" to e("생활", "구독"), ANY to e("생활", "통신")),
        (E to "교통") to mapOf("택시" to e("교통", "택시"), ANY to e("교통", "대중교통")),
        (E to "차량") to mapOf("보험/자동차세" to e("기타", "기타"), ANY to e("교통", "주유/주차")),
        (E to "쇼핑") to mapOf(
            "의류/패션" to e("쇼핑", "의류/미용"), "뷰티/미용" to e("쇼핑", "의류/미용"), ANY to e("쇼핑", "온라인쇼핑"),
        ),
        (E to "문화/여가") to mapOf(ANY to e("여가", "문화")),
        (E to "여행") to mapOf(ANY to e("여가", "여행")),
        (E to "의료") to mapOf(ANY to e("기타", "의료")),
        (E to "경조사/선물") to mapOf(ANY to e("기타", "경조사/선물")),
        (E to "기타") to mapOf(ANY to e("기타", "기타")),
        (I to "월급") to mapOf(ANY to i("월급")),
        (I to "부수입") to mapOf(
            "이자/배당" to i("캐시백/이자"), "캐시백/포인트" to i("캐시백/이자"), "용돈" to i("용돈"), ANY to i("기타"),
        ),
        (I to "기타 수입") to mapOf(ANY to i("기타")),
        (S to "적금/예금") to mapOf("적금" to s("적금"), ANY to s("기타")),
        (S to "투자") to mapOf(ANY to s("투자")),
        (S to "연금") to mapOf(ANY to s("연금")),
        (S to "청약") to mapOf(ANY to s("청약")),
        (S to "저축") to mapOf("투자" to s("투자"), "연금저축" to s("연금"), "청약" to s("청약"), ANY to s("기타")),
    )

    fun map(kind: CategoryKind, parent: String, child: String): LegacyTarget {
        val byChild = TABLE[kind to parent]
        val path = byChild?.get(child) ?: byChild?.get(ANY) ?: FixedCategories.otherLeaf(kind)
        // 원래 기타였던 거래는 흔적(메모 태그)이 필요 없다.
        val wasOther = child == "기본" && ((kind == E && parent == "기타") || (kind == I && parent == "기타 수입"))
        return LegacyTarget(path, tagMemo = path.child == "기타" && !wasOther)
    }
}
