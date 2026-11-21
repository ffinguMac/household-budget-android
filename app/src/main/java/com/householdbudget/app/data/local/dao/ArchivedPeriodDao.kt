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

    /** 합계 칼럼은 저장된 스냅샷 대신 지금 거래로 계산한다(지난 거래를 고쳐도 따라온다). */
    @Query(
        """
        SELECT a.id, a.start_epoch_day, a.end_epoch_day, a.archived_at_epoch_ms,
               COALESCE(SUM(CASE WHEN t.kind = 'INCOME'  THEN t.amount_minor ELSE 0 END), 0) AS total_income_minor,
               COALESCE(SUM(CASE WHEN t.kind = 'EXPENSE' THEN t.amount_minor ELSE 0 END), 0) AS total_expense_minor,
               COALESCE(SUM(CASE WHEN t.kind = 'SAVINGS' THEN t.amount_minor ELSE 0 END), 0) AS total_savings_minor
        FROM archived_periods a
        LEFT JOIN transactions t
               ON t.occurred_epoch_day >= a.start_epoch_day AND t.occurred_epoch_day < a.end_epoch_day
        GROUP BY a.id ORDER BY a.start_epoch_day DESC
        """,
    )
    fun observeAll(): Flow<List<ArchivedPeriodEntity>>

    // 기준일에 걸친 기간은 남긴다(합계는 남은 거래로 실시간 계산된다).
    @Query("SELECT COUNT(*) FROM archived_periods WHERE end_epoch_day <= :epochDay")
    suspend fun countEndingByOrBefore(epochDay: Long): Int

    @Query("DELETE FROM archived_periods WHERE end_epoch_day <= :epochDay")
    suspend fun deleteEndingByOrBefore(epochDay: Long): Int

    @Query("SELECT * FROM archived_periods WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ArchivedPeriodEntity?
}
