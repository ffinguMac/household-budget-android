package com.householdbudget.app.data.local.model

/**
 * Room 조회 결과 (거래 + 소분류 + 대분류 정보).
 *
 * @property amountMinor 항상 양수.
 * @property kind 거래 종류 — INCOME / EXPENSE / SAVINGS.
 * @property categoryId 거래가 직접 연결된 소분류(leaf) id.
 * @property parentCategoryId 소분류의 대분류 id. 정상 데이터에선 항상 non-null.
 */
data class TransactionWithCategoryRow(
    val id: Long,
    val occurredEpochDay: Long,
    val amountMinor: Long,
    val kind: String,
    val categoryId: Long,
    val categoryName: String,
    val parentCategoryId: Long?,
    val parentCategoryName: String?,
    val memo: String,
)
