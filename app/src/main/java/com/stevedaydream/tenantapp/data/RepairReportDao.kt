package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RepairReportDao {
    @Insert
    suspend fun insert(report: RepairReport)

    @Query("SELECT * FROM repair_reports ORDER BY date DESC")
    fun getAll(): Flow<List<RepairReport>>
}
