package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repair_reports")
data class RepairReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tenantName: String,
    val roomNumber: String,
    val issue: String,
    val description: String,
    val date: Long = System.currentTimeMillis()
)
