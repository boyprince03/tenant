package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_change_requests")
data class RoomChangeRequest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tenantId: Int,
    val tenantName: String,
    val landlordCode: String,
    val currentRoomNumber: String,
    val requestedRoomNumber: String,
    val requestDate: Long = System.currentTimeMillis(),
    var status: String = "pending" // pending, approved, rejected
)