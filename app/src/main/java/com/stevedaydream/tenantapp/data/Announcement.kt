package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "announcements")
data class Announcement(
    @PrimaryKey val id: String = "",
    val title: String = "",
    val content: String = "",
    val date: Long = System.currentTimeMillis(),
    val landlordCode: String? = null // null 代表全域公告
)
