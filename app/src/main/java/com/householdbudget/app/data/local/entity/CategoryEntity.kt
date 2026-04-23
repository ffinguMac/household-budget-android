package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys =
        [
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["parent_id"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [
        Index(value = ["sort_order"]),
        Index(value = ["parent_id"]),
        Index(value = ["kind"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "INCOME" | "EXPENSE" | "SAVINGS" — see [com.householdbudget.app.domain.CategoryKind]. */
    val kind: String,
    /** null = 대분류 (top-level). non-null = 소분류 (leaf) under the given parent. */
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)
