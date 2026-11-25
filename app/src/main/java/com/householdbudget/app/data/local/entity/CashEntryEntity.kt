package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 비상금 기록. 예산·통계·거래와 따로 관리한다. amountMinor: 넣기 +, 빼기 −. */
@Entity(tableName = "cash_entries", indices = [Index(value = ["occurred_epoch_day"])])
data class CashEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "occurred_epoch_day") val occurredEpochDay: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "memo", defaultValue = "''") val memo: String = "",
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)
