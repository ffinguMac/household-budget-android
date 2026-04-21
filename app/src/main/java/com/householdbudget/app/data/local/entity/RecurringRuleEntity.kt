package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_rules",
    foreignKeys =
        [
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["category_id"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["category_id"])],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "is_income") val isIncome: Boolean,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val memo: String = "",
    val enabled: Boolean = true,
    /** 마지막으로 자동 반영한 달 `yyyy-MM` (달력 기준). */
    @ColumnInfo(name = "last_applied_year_month") val lastAppliedYearMonth: String? = null,
)
