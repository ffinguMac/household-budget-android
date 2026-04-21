package com.householdbudget.app.data.local.model

/**
 * Room 조회 결과(거래 + 카테고리명).
 *
 * @property amountMinor 항상 양수. 수입/지출은 [isIncome]으로 구분.
 */
data class TransactionWithCategoryRow(
    val id: Long,
    val occurredEpochDay: Long,
    val amountMinor: Long,
    val isIncome: Boolean,
    val categoryId: Long,
    val categoryName: String,
    val memo: String,
)
