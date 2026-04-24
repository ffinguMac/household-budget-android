package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 카테고리(주로 소분류 leaf)별 월간 예산 한도.
 *
 * categoryId를 PK로 사용 — 한 카테고리에 예산 1개만 있을 수 있다.
 * 카테고리가 삭제되면 예산도 함께 삭제된다 (CASCADE).
 */
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
