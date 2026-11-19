package com.householdbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.householdbudget.app.data.local.entity.NotificationLogEntity

@Dao
interface NotificationLogDao {
    /** 같은 알림이 다시 오면 -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: NotificationLogEntity): Long

    @Query(
        "SELECT COUNT(*) FROM notification_log WHERE package_name = :pkg AND content_hash = :hash " +
            "AND post_time_ms BETWEEN :fromMs AND :toMs",
    )
    suspend fun countSimilar(pkg: String, hash: String, fromMs: Long, toMs: Long): Int

    @Query("DELETE FROM notification_log WHERE post_time_ms < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM notification_log")
    suspend fun count(): Int
}
