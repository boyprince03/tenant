package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "announcements")
data class Announcement(
    // 【*** 修正：將 id 從 Int 改為 String 以匹配 Firestore 文件 ID ***】
    @PrimaryKey val id: String = "",
    val title: String,
    val content: String,
    val date: Long = System.currentTimeMillis(),
    val landlordCode: String? = null // null 代表全域公告
)