package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnouncementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(announcement: Announcement)

    @Update
    suspend fun update(announcement: Announcement)

    @Delete
    suspend fun delete(announcement: Announcement)

    @Query("SELECT * FROM announcements ORDER BY date DESC")
    fun getAll(): Flow<List<Announcement>>

    @Query("SELECT * FROM announcements WHERE landlordCode IS NULL OR landlordCode = :landlordCode ORDER BY date DESC")
    fun getGlobalAndByLandlordCode(landlordCode: String): Flow<List<Announcement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(announcements: List<Announcement>) // 新增
}