package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey(autoGenerate = false) val roomNumber: String,
    val tenantName: String = "",         // 租客姓名
    val tenantId: Int? = null,
    val type: String = "",               // 房型
    val note: String = "",               // 備註
    val rentAmount: Int = 0,             // 租金
    val deposit: Int = 0,                // 押金
    val status: String = "",             // 房屋狀態（如：出租中/空房/維修中）
    val rentStartDate: String = "",      // 租賃開始日期 yyyy-MM-dd
    val rentEndDate: String = "",        // 租賃結束日期 yyyy-MM-dd
    val rentDuration: String = "",        // 租賃期間（如："1年" 或 "12個月"）
    var landlordCode: String? = null,
    // --- 新增欄位 ---
    val address: String = "未提供",       // 房屋地址
    val floor: String = "未提供",         // 房間樓層
    val size: String = "未提供"          // 坪數
)