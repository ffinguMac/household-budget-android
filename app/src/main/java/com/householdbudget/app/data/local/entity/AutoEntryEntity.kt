package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object AutoEntryStatus {
    const val REGISTERED = "REGISTERED"
    const val PENDING = "PENDING"
    const val IGNORED = "IGNORED"
}

/** 외래 키 없음: 로그는 90일 뒤 지워지고 거래는 사용자가 지울 수 있다. */
@Entity(
    tableName = "auto_entries",
    indices = [Index(value = ["status"]), Index(value = ["occurred_epoch_day", "amount_minor"])],
)
data class AutoEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "log_id") val logId: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "occurred_epoch_day") val occurredEpochDay: Long,
    @ColumnInfo(name = "posted_at_ms") val postedAtMs: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    /** Direction.name */
    val direction: String,
    val merchant: String,
    @ColumnInfo(name = "merchant_key") val merchantKey: String,
    val status: String,
    /** ReviewReason.name */
    val reason: String? = null,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
)
