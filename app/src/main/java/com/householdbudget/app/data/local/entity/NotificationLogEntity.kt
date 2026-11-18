package com.householdbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_log",
    indices =
        [
            Index(value = ["package_name", "notification_key", "post_time_ms", "content_hash"], unique = true),
            Index(value = ["post_time_ms"]),
        ],
)
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "notification_key") val notificationKey: String,
    @ColumnInfo(name = "post_time_ms") val postTimeMs: Long,
    val title: String,
    val body: String,
    @ColumnInfo(name = "content_hash") val contentHash: String,
)
