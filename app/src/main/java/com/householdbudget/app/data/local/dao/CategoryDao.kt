package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.householdbudget.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query(
        "SELECT c.* FROM categories c JOIN categories p ON p.id = c.parent_id " +
            "WHERE c.kind = :kind AND p.name = :parentName AND c.name = :childName LIMIT 1",
    )
    suspend fun findLeafByPath(kind: String, parentName: String, childName: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE parent_id = :parentId ORDER BY sort_order ASC, id ASC")
    suspend fun childrenOf(parentId: Long): List<CategoryEntity>

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}
