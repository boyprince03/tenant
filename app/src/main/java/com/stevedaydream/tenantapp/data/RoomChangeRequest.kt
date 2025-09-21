// tenantapp/data/RoomChangeRequest.kt

package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID // <-- 新增 import

@Entity(tableName = "room_change_requests")
data class RoomChangeRequest(
    // 【*** 核心修改：將主鍵改為 String UUID ***】
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    val tenantId: String,
    val tenantName: String,
    val landlordCode: String,
    val currentRoomNumber: String,
    val requestedRoomNumber: String,
    val requestDate: Long = System.currentTimeMillis(),
    var status: String = "pending" // pending, approved, rejected
)