package com.stevedaydream.tenantapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomNumber: String,
    val recordMonth: String, // "YYYY-MM"
    val rentAmount: Int,
    val electricityFee: Int,
    val totalAmount: Int,
    var isPaid: Boolean = false,
    val paymentDate: Long? = null // 繳費日期
)