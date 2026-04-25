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

    // ── 백업 / 복원 ───────────────────────────────────────────────────────────
    @Query("SELECT * FROM category_budgets ORDER BY category_id ASC")
    suspend fun getAll(): List<CategoryBudgetEntity>

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(budgets: List<CategoryBudgetEntity>)
}
