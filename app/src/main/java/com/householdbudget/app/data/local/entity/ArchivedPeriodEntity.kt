package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "archived_periods",
    indices =
        [
            Index(
                value = ["start_epoch_day", "end_epoch_day"],
                unique = true,
            ),
            Index(value = ["start_epoch_day"]),
        ],
)
data class ArchivedPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [start, end) 구간의 시작일 (epoch day). */
    @ColumnInfo(name = "start_epoch_day") val startEpochDay: Long,
    /** [start, end) 구간의 끝(미포함) = 다음 회계월 시작일 epoch day. */
    @ColumnInfo(name = "end_epoch_day") val endEpochDay: Long,
    @ColumnInfo(name = "total_income_minor") val totalIncomeMinor: Long,
    @ColumnInfo(name = "total_expense_minor") val totalExpenseMinor: Long,
    @ColumnInfo(name = "total_savings_minor") val totalSavingsMinor: Long = 0L,
    @ColumnInfo(name = "archived_at_epoch_ms") val archivedAtEpochMs: Long,
)
