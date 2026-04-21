package com.householdbudget.app.data.local.model

import androidx.room.ColumnInfo

/** 달력 한 날짜의 수입·지출 합계(지출은 양수 합). */
data class DayTotalRow(
    @ColumnInfo(name = "dayEpoch") val dayEpoch: Long,
    @ColumnInfo(name = "incomeMinor") val incomeMinor: Long,
    @ColumnInfo(name = "expenseMinor") val expenseMinor: Long,
)
