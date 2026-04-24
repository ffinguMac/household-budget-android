package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.householdbudget.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query(
        "SELECT * FROM categories WHERE parent_id IS NULL AND kind = :kind " +
            "ORDER BY sort_order ASC, id ASC",
    )
    fun observeTopLevelByKind(kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories WHERE parent_id IS NULL AND kind = :kind")
    suspend fun countTopLevelByKind(kind: String): Int

    @Query(
        "SELECT * FROM categories WHERE parent_id = :parentId " +
            "ORDER BY sort_order ASC, id ASC",
    )
    fun observeChildren(parentId: Long): Flow<List<CategoryEntity>>

    @Query(
        "SELECT * FROM categories WHERE parent_id = :parentId " +
            "ORDER BY sort_order ASC, id ASC",
    )
    suspend fun getChildren(parentId: Long): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE parent_id = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :leafId")
    suspend fun countTransactionsForLeaf(leafId: Long): Int

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE category_id = :leafId")
    suspend fun countRecurringRulesForLeaf(leafId: Long): Int

    @Query(
        "SELECT * FROM categories WHERE parent_id = :parentId AND name = :name LIMIT 1",
    )
    suspend fun findChildByName(parentId: Long, name: String): CategoryEntity?

    @Query("SELECT MAX(sort_order) FROM categories WHERE parent_id = :parentId")
    suspend fun maxChildSortOrder(parentId: Long): Int?

    @Query("SELECT MAX(sort_order) FROM categories WHERE parent_id IS NULL AND kind = :kind")
    suspend fun maxTopLevelSortOrder(kind: String): Int?

    @Query(
        "SELECT * FROM categories WHERE parent_id IS NULL AND kind = :kind AND name = :name LIMIT 1",
    )
    suspend fun findTopLevelByName(kind: String, name: String): CategoryEntity?
}
