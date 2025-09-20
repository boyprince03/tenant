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
    suspend fun getAllRoomsNow(): List<RoomEntity> // <-- 改為 suspend fun

    @Delete
    suspend fun deleteRoom(room: RoomEntity)

    @Query("SELECT * FROM rooms WHERE landlordCode = :code")
    suspend fun getRoomsByLandlordCode(code: String): List<RoomEntity> // <-- 改為 suspend fun

    @Query("SELECT * FROM rooms WHERE landlordCode IS NULL")
    suspend fun getUnassignedRooms(): List<RoomEntity> // <-- 改為 suspend fun

    @Query("SELECT * FROM rooms")
    fun getAll(): Flow<List<RoomEntity>>

    // --- 【*** 新增此方法 ***】 ---
    @Query("SELECT * FROM rooms WHERE roomNumber = :roomNumber LIMIT 1")
    suspend fun getRoomByNumber(roomNumber: String): RoomEntity?
    @Query("SELECT * FROM rooms WHERE landlordCode = :code ORDER BY roomNumber ASC")
    fun getRoomsByLandlordCodeFlow(code: String): Flow<List<RoomEntity>>
}