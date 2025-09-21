package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RepairReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: RepairReport)

    @Update
    suspend fun update(report: RepairReport)

    @Query("SELECT * FROM repair_reports ORDER BY date DESC")
    fun getAll(): Flow<List<RepairReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(reports: List<RepairReport>) // 新增
}