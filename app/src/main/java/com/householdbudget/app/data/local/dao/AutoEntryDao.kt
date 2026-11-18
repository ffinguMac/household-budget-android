package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.householdbudget.app.data.local.entity.AutoEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoEntryDao {
    @Insert
    suspend fun insert(e: AutoEntryEntity): Long

    @Update
    suspend fun update(e: AutoEntryEntity)

    @Query("SELECT * FROM auto_entries WHERE id = :id")
    suspend fun getById(id: Long): AutoEntryEntity?

    @Query("SELECT * FROM auto_entries WHERE transaction_id = :transactionId LIMIT 1")
    suspend fun findByTransactionId(transactionId: Long): AutoEntryEntity?

    @Query("SELECT * FROM auto_entries WHERE status = 'PENDING' AND merchant_key = :merchantKey AND direction = :direction")
    suspend fun listPendingByMerchantKey(merchantKey: String, direction: String): List<AutoEntryEntity>

    @Query("SELECT * FROM auto_entries WHERE status = :status ORDER BY posted_at_ms DESC")
    fun observeByStatus(status: String): Flow<List<AutoEntryEntity>>

    @Query("SELECT COUNT(*) FROM auto_entries WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    /** 다른 앱이 같은 금액·날짜로 이미 기록(또는 검토 대기)한 지출 수. */
    @Query(
        "SELECT COUNT(*) FROM auto_entries WHERE direction = 'OUT' AND amount_minor = :amountMinor " +
            "AND occurred_epoch_day = :epochDay AND package_name != :excludePackage " +
            "AND status IN ('REGISTERED', 'PENDING') AND posted_at_ms BETWEEN :fromMs AND :toMs",
    )
    suspend fun countCrossAppOut(amountMinor: Long, epochDay: Long, excludePackage: String, fromMs: Long, toMs: Long): Int
}
