package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY roomNumber ASC")
    fun getAllRooms(): Flow<List<RoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRooms(rooms: List<RoomEntity>)
    @Query("SELECT * FROM rooms ORDER BY roomNumber ASC")
    fun getAllRoomsNow(): List<RoomEntity>
    @Delete
    suspend fun deleteRoom(room: RoomEntity)
    @Query("SELECT * FROM rooms WHERE landlordCode = :code")
    fun getRoomsByLandlordCode(code: String): List<RoomEntity>
    // 新增此行以支援 ExcelImportScreen 中的重複檢查功能
    @Query("SELECT * FROM rooms")
    fun getAll(): Flow<List<RoomEntity>>
}
