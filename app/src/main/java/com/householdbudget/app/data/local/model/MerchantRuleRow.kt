package com.householdbudget.app.data.local.model

data class MerchantRuleRow(
    val merchantKey: String,
    val direction: String,
    val displayName: String,
    val categoryId: Long?,
    val categoryName: String?,
    val parentCategoryName: String?,
)
