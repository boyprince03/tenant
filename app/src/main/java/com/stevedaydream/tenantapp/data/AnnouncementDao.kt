package com.stevedaydream.tenantapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnouncementDao {
    @Insert suspend fun insert(announcement: Announcement)
    @Update suspend fun update(announcement: Announcement)
    @Delete suspend fun delete(announcement: Announcement)

    @Query("SELECT * FROM announcements ORDER BY date DESC")
    fun getAll(): Flow<List<Announcement>>

    // 新增此方法：取得全域公告 (landlordCode IS NULL) 以及特定房東的公告
    @Query("SELECT * FROM announcements WHERE landlordCode IS NULL OR landlordCode = :landlordCode ORDER BY date DESC")
    fun getGlobalAndByLandlordCode(landlordCode: String): Flow<List<Announcement>>
}