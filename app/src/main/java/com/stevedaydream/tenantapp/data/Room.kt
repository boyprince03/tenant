// tenantapp/data/Room.kt

package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey(autoGenerate = false) val roomNumber: String,
    val tenantName: String = "",
    val tenantId: String? = null, // 【*** 修正：Int? -> String? ***】
    val type: String = "",
    val note: String = "",
    val rentAmount: Int = 0,
    val deposit: Int = 0,
    val status: String = "",
    val rentStartDate: String = "",
    val rentEndDate: String = "",
    val rentDuration: String = "",
    var landlordCode: String? = null,
    val address: String = "未提供",
    val floor: String = "未提供",
    val size: String = "未提供"
)