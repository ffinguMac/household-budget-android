package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.householdbudget.app.data.local.entity.ArchivedPeriodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchivedPeriodDao {
    @Query(
        """
        SELECT COUNT(*) FROM archived_periods
        WHERE start_epoch_day = :startEx AND end_epoch_day = :endEx
        """,
    )
    suspend fun countByBounds(startEx: Long, endEx: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ArchivedPeriodEntity): Long

    @Query("SELECT * FROM archived_periods ORDER BY start_epoch_day DESC")
    fun observeAll(): Flow<List<ArchivedPeriodEntity>>

    @Query("SELECT * FROM archived_periods WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ArchivedPeriodEntity?
}
