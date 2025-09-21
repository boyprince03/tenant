package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// tenantapp/data/RepairReport.kt

@Entity(tableName = "repair_reports")
data class RepairReport(
    // 【*** 修正：將 id 從 Int 改為 String ***】
    @PrimaryKey val id: String = "",
    val tenantName: String,
    val roomNumber: String,
    val issue: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val status: String = "待處理" // 新增狀態欄位，預設為待處理
)