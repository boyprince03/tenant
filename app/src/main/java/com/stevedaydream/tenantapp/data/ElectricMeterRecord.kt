// ElectricMeterRecord.kt
package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "electric_meter_records")
data class ElectricMeterRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomNumber: String,
    val recordMonth: String, // "2025-07"
    val meterValue: Int
)
