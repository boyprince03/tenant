// tenantapp/data/RoomChangeRequestDao.kt

package com.stevedaydream.tenantapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomChangeRequestDao {
    // 【*** 新增此方法，用於從 Firestore 同步 ***】
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(requests: List<RoomChangeRequest>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) // <-- 建議加上 REPLACE
    suspend fun insert(request: RoomChangeRequest)

    @Update
    suspend fun update(request: RoomChangeRequest)

    @Query("SELECT * FROM room_change_requests WHERE landlordCode = :landlordCode ORDER BY requestDate DESC")
    fun getRequestsByLandlord(landlordCode: String): Flow<List<RoomChangeRequest>>

    @Query("SELECT * FROM room_change_requests WHERE id = :requestId")
    suspend fun getRequestById(requestId: String): RoomChangeRequest? // 【*** 修正：Int -> String ***】

    @Query("SELECT * FROM room_change_requests WHERE tenantId = :tenantId ORDER BY requestDate DESC LIMIT 1")
    fun getLatestRequestByTenantId(tenantId: String): Flow<RoomChangeRequest?>
}