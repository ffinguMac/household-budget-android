package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "merchant_rules", primaryKeys = ["merchant_key", "direction"])
data class MerchantRuleEntity(
    @ColumnInfo(name = "merchant_key") val merchantKey: String,
    /** Direction.name */
    val direction: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** null = 기록 안 함 */
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)
