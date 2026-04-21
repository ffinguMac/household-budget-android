package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys =
        [
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["category_id"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["occurred_epoch_day"]),
            Index(value = ["category_id"]),
        ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "occurred_epoch_day") val occurredEpochDay: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "is_income") val isIncome: Boolean,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val memo: String = "",
)
