// ElectricMeterRecord.kt
package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "electric_meter_records")
data class ElectricMeterRecord(
    // 【*** 修正：將 id 從 Int 改為 String ***】
    @PrimaryKey val id: String = "",
    val roomNumber: String,
    val recordMonth: String, // "2025-07"
    val meterValue: Int
)