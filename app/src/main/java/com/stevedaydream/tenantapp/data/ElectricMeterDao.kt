// ElectricMeterDao.kt
package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ElectricMeterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: ElectricMeterRecord)

    @Query("SELECT * FROM electric_meter_records WHERE recordMonth = :month")
    fun getAllForMonth(month: String): Flow<List<ElectricMeterRecord>>

    @Query("SELECT * FROM electric_meter_records WHERE roomNumber = :roomNo ORDER BY recordMonth DESC LIMIT 2")
    suspend fun getLastTwoRecords(roomNo: String): List<ElectricMeterRecord>

    @Query("SELECT * FROM electric_meter_records WHERE roomNumber = :roomNo AND recordMonth = :month LIMIT 1")
    suspend fun getRecord(roomNo: String, month: String): ElectricMeterRecord?
    @Query("SELECT * FROM electric_meter_records")
    fun getAllRecords(): Flow<List<ElectricMeterRecord>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecords(records: List<ElectricMeterRecord>)
    // 新增此行以支援 ExcelImportScreen 中的重複檢查功能
    @Query("SELECT * FROM electric_meter_records")
    fun getAll(): Flow<List<ElectricMeterRecord>>

}
