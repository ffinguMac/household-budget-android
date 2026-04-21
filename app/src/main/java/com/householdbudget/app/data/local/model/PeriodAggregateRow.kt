package com.householdbudget.app.data.local.model

import androidx.room.ColumnInfo

/** 회계 구간 [start, end) 안의 수입·지출 합계. */
data class PeriodAggregateRow(
    @ColumnInfo(name = "incomeMinor") val incomeMinor: Long,
    @ColumnInfo(name = "expenseMinor") val expenseMinor: Long,
)
