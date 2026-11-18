package com.householdbudget.app.domain.category

import com.householdbudget.app.domain.CategoryKind

data class FixedParent(val kind: CategoryKind, val name: String, val icon: String, val children: List<String>)

data class CategoryPath(val kind: CategoryKind, val parent: String, val child: String)

object FixedCategories {
    val TREE: List<FixedParent> = listOf(
        FixedParent(CategoryKind.EXPENSE, "식비", "🍽️", listOf("식사", "카페", "간식", "배달", "장보기")),
        FixedParent(CategoryKind.EXPENSE, "교통", "🚌", listOf("대중교통", "택시", "주유/주차")),
        FixedParent(CategoryKind.EXPENSE, "생활", "🏠", listOf("통신", "공과금", "생활용품", "구독")),
        FixedParent(CategoryKind.EXPENSE, "쇼핑", "🛒", listOf("의류/미용", "온라인쇼핑", "간편결제 충전")),
        FixedParent(CategoryKind.EXPENSE, "여가", "🎭", listOf("문화", "여행", "술/모임")),
        FixedParent(CategoryKind.EXPENSE, "기타", "📦", listOf("의료", "경조사/선물", "기타")),
        FixedParent(CategoryKind.EXPENSE, "유동 지출", "💸", listOf("기본")),
        FixedParent(CategoryKind.INCOME, "수입", "💰", listOf("월급", "캐시백/이자", "용돈", "기타")),
        FixedParent(CategoryKind.SAVINGS, "저축", "🏦", listOf("적금", "투자", "연금", "청약", "기타")),
    )

    fun otherLeaf(kind: CategoryKind): CategoryPath = when (kind) {
        CategoryKind.EXPENSE -> CategoryPath(kind, "기타", "기타")
        CategoryKind.INCOME -> CategoryPath(kind, "수입", "기타")
        CategoryKind.SAVINGS -> CategoryPath(kind, "저축", "기타")
    }
}
