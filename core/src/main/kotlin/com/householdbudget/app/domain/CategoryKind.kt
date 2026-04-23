package com.householdbudget.app.domain

enum class CategoryKind(val storage: String) {
    INCOME("INCOME"),
    EXPENSE("EXPENSE"),
    SAVINGS("SAVINGS"),
    ;

    companion object {
        fun fromStorage(value: String): CategoryKind =
            entries.firstOrNull { it.storage == value } ?: EXPENSE
    }
}
