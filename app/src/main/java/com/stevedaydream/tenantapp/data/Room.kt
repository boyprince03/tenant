package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "rooms")
data class RoomEntity(
    // 【核心修改】新增 id 作為主鍵，並用 UUID 產生唯一值
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // roomNumber 變為一般欄位
    val roomNumber: String = "",
    val tenantName: String = "",
    val tenantId: String? = null,
    val type: String = "",
    val note: String = "",
    val rentAmount: Int = 0,
    val deposit: Int = 0,
    val status: String = "可租", // 提供預設值
    val rentStartDate: String = "",
    val rentEndDate: String = "",
    val rentDuration: String = "",
    var landlordCode: String? = null,
    val address: String = "未提供", // 提供預設值
    val floor: String = "未提供", // 提供預設值
    val size: String = "未提供" // 提供預設值
)
