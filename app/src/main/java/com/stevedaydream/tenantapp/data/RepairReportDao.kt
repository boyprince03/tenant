package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// tenantapp/data/RepairReportDao.kt

@Dao
interface RepairReportDao {
    @Insert
    suspend fun insert(report: RepairReport)

    // --- 【核心修改】 ---
    @Update
    suspend fun update(report: RepairReport) // 新增 Update 方法

    @Query("SELECT * FROM repair_reports ORDER BY date DESC")
    fun getAll(): Flow<List<RepairReport>>
}
