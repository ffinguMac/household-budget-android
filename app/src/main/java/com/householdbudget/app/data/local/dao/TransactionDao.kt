package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.householdbudget.app.data.local.entity.TransactionEntity
import com.householdbudget.app.data.local.model.DayTotalRow
import com.householdbudget.app.data.local.model.PeriodAggregateRow
import com.householdbudget.app.data.local.model.TransactionWithCategoryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Delete
    suspend fun delete(entity: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE category_id = :leafId")
    suspend fun deleteByCategoryId(leafId: Long)

    @Query(
        """
        SELECT t.id AS id,
               t.occurred_epoch_day AS occurredEpochDay,
               t.amount_minor AS amountMinor,
               t.kind AS kind,
               t.category_id AS categoryId,
               c.name AS categoryName,
               c.parent_id AS parentCategoryId,
               p.name AS parentCategoryName,
               t.memo AS memo
        FROM transactions t
        INNER JOIN categories c ON c.id = t.category_id
        LEFT JOIN categories p ON p.id = c.parent_id
        WHERE t.occurred_epoch_day >= :startEx
          AND t.occurred_epoch_day < :endEx
        ORDER BY t.occurred_epoch_day DESC, t.id DESC
        """,
    )
    fun observeBetween(startEx: Long, endEx: Long): Flow<List<TransactionWithCategoryRow>>

    @Query(
        """
        SELECT t.id AS id,
               t.occurred_epoch_day AS occurredEpochDay,
               t.amount_minor AS amountMinor,
               t.kind AS kind,
               t.category_id AS categoryId,
               c.name AS categoryName,
               c.parent_id AS parentCategoryId,
               p.name AS parentCategoryName,
               t.memo AS memo
        FROM transactions t
        INNER JOIN categories c ON c.id = t.category_id
        LEFT JOIN categories p ON p.id = c.parent_id
        WHERE t.id = :id
        LIMIT 1
        """,
    )
    suspend fun getWithCategoryById(id: Long): TransactionWithCategoryRow?

    @Query(
        """
        SELECT occurred_epoch_day AS dayEpoch,
               COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amount_minor ELSE 0 END), 0) AS incomeMinor,
               COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amount_minor ELSE 0 END), 0) AS expenseMinor,
               COALESCE(SUM(CASE WHEN kind = 'SAVINGS' THEN amount_minor ELSE 0 END), 0) AS savingsMinor
        FROM transactions
        WHERE occurred_epoch_day >= :startEx AND occurred_epoch_day < :endEx
        GROUP BY occurred_epoch_day
        """,
    )
    fun observeDayTotalsBetween(startEx: Long, endEx: Long): Flow<List<DayTotalRow>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amount_minor ELSE 0 END), 0) AS incomeMinor,
            COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amount_minor ELSE 0 END), 0) AS expenseMinor,
            COALESCE(SUM(CASE WHEN kind = 'SAVINGS' THEN amount_minor ELSE 0 END), 0) AS savingsMinor
        FROM transactions
        WHERE occurred_epoch_day >= :startEx AND occurred_epoch_day < :endEx
        """,
    )
    suspend fun aggregateBetween(startEx: Long, endEx: Long): PeriodAggregateRow
}
