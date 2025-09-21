package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repair_reports")
data class RepairReport(
    @PrimaryKey val id: String = "",
    val tenantName: String = "",
    val roomNumber: String = "",
    val issue: String = "",
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val status: String = "待處理"
)
