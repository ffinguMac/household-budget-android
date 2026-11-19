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

    @Query("UPDATE notification_log SET title = :title, body = :body WHERE id = :id")
    suspend fun setText(id: Long, title: String, body: String)

    /** 항목을 만들지 않은 로그와 보관 기간이 지난 로그의 원문을 비운다. */
    @Query(
        "UPDATE notification_log SET title = '', body = '' WHERE (title != '' OR body != '') " +
            "AND (post_time_ms < :cutoffMs OR id NOT IN (SELECT log_id FROM auto_entries))",
    )
    suspend fun clearStaleText(cutoffMs: Long): Int

    @Query("DELETE FROM notification_log WHERE post_time_ms < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("SELECT COUNT(*) FROM notification_log")
    suspend fun count(): Int
}
