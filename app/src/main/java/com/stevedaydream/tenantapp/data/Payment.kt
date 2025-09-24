package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey val id: String = "",
    val roomNumber: String = "",
    val recordMonth: String = "", // "YYYY-MM"
    val rentAmount: Int = 0,
    val electricityFee: Int = 0,
    val totalAmount: Int = 0,
    var isPaid: Boolean = false,
    val paymentDate: Long? = null, // 繳費日期
    val screenshotUrl: String? = null // 【*** 新增的欄位應在此處 ***】
)