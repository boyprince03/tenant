package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "room_change_requests")
data class RoomChangeRequest(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val tenantId: String = "",
    val tenantName: String = "",
    val landlordCode: String = "",
    val currentRoomNumber: String = "",
    val requestedRoomNumber: String = "",
    val requestDate: Long = System.currentTimeMillis(),
    var status: String = "pending" // pending, approved, rejected
)
