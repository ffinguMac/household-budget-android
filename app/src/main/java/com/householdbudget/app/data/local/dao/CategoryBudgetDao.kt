package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.householdbudget.app.data.local.entity.CategoryBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets")
    fun observeAll(): Flow<List<CategoryBudgetEntity>>

    @Query("SELECT * FROM category_budgets WHERE category_id = :categoryId LIMIT 1")
    suspend fun getByCategoryId(categoryId: Long): CategoryBudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets WHERE category_id = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long)
}
