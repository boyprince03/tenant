package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(payment: Payment)

    @Query("SELECT * FROM payments WHERE roomNumber = :roomNo AND recordMonth = :month")
    fun getPaymentRecord(roomNo: String, month: String): Flow<Payment?>

    @Query("SELECT * FROM payments WHERE roomNumber = :roomNo ORDER BY recordMonth DESC")
    fun getAllPaymentsForRoom(roomNo: String): Flow<List<Payment>>

    // --- 【*** 新增此方法 ***】 ---
    @Query("SELECT * FROM payments WHERE roomNumber = :roomNo AND recordMonth = :month LIMIT 1")
    suspend fun getPaymentRecordNow(roomNo: String, month: String): Payment?
}