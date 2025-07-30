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
}
