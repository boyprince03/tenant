package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    // 【核心修改】主鍵改為 String，用來存放 Firebase UID
    @PrimaryKey val id: String = "",

    // 【核心修改】為所有欄位提供預設值，並移除 password
    val username: String = "",
    val phone: String = "",
    val idNumber: String = "",
    val role: String = "",
    val landlordCode: String? = null,
    var boundRoomNumber: String? = null,
    var boundLandlordCode: String? = null,
    val bankAccountName: String? = null,
    val bankAccountNumber: String? = null
)