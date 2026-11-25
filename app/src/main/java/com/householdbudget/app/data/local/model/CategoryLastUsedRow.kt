package com.householdbudget.app.data.local.model

/** 카테고리별 마지막 거래일. 직접 추가 세부의 숨김 판정용. */
data class CategoryLastUsedRow(val categoryId: Long, val lastEpochDay: Long)
