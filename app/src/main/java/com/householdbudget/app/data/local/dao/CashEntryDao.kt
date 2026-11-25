package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.householdbudget.app.data.local.entity.CashEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashEntryDao {
    @Query("SELECT * FROM cash_entries ORDER BY occurred_epoch_day DESC, id DESC")
    fun observeAll(): Flow<List<CashEntryEntity>>

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM cash_entries")
    fun observeBalance(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM cash_entries")
    suspend fun balance(): Long

    @Query("SELECT * FROM cash_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CashEntryEntity?

    @Insert
    suspend fun insert(entity: CashEntryEntity): Long

    @Update
    suspend fun update(entity: CashEntryEntity)

    @Delete
    suspend fun delete(entity: CashEntryEntity)
}
