package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_budgets",
    foreignKeys =
        [
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["category_id"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class CategoryBudgetEntity(
    @PrimaryKey
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "monthly_amount_minor") val monthlyAmountMinor: Long,
    val enabled: Boolean = true,
)
