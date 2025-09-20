package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val password: String,
    val phone: String,
    val idNumber: String,
    val role: String,
    val landlordCode: String? = null, // 房東自己的序號
    var boundRoomNumber: String? = null, // 租客綁定的房號
    var boundLandlordCode: String? = null, // 租客綁定的房東序號
    // --- 新增欄位 ---
    val bankAccountName: String? = null, // 房東收款戶名
    val bankAccountNumber: String? = null // 房東收款帳號
)