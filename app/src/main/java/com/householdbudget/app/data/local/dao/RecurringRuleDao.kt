package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.householdbudget.app.data.local.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY day_of_month ASC, id ASC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE enabled = 1")
    suspend fun listEnabled(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE category_id = :categoryId")
    suspend fun listByCategoryId(categoryId: Long): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: RecurringRuleEntity): Long

    @Update
    suspend fun update(entity: RecurringRuleEntity)

    @Delete
    suspend fun delete(entity: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE category_id = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long)
}
