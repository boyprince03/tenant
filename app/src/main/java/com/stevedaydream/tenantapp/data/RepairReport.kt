package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// tenantapp/data/RepairReport.kt

@Entity(tableName = "repair_reports")
data class RepairReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tenantName: String,
    val roomNumber: String,
    val issue: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    // --- 【核心修改】 ---
    val status: String = "待處理" // 新增狀態欄位，預設為待處理
)