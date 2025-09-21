package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RoomRepository(private val roomDao: RoomDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val roomsCollection = firestore.collection("rooms")

    /**
     * [讀取]
     * 從本地資料庫取得特定房東的所有房間資料流。
     * 同時，建立一個 Firestore 的即時監聽器，當雲端資料變動時，會自動更新本地資料庫，
     * UI 因為訂閱了這個資料流，也會自動更新。
     */
    fun getRoomsForLandlord(landlordCode: String): Flow<List<RoomEntity>> {
        val query = roomsCollection.whereEqualTo("landlordCode", landlordCode)

        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("RoomRepository", "Listen failed.", error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val rooms = snapshot.toObjects<RoomEntity>()
                CoroutineScope(Dispatchers.IO).launch {
                    // 當雲端有變動，就更新本地
                    roomDao.insertRooms(rooms)
                }
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    roomDao.deleteRoomsByLandlordCode(landlordCode)
                }
            }
        }
        // UI 永遠從本地 Room 讀取
        return roomDao.getRoomsByLandlordCodeFlow(landlordCode)
    }

    /**
     * [一次性讀取]
     * 從 Firestore 獲取可租用的房間 (例如給租客選擇時使用)。
     * 讀取後也會更新本地快取。
     */
    suspend fun getAvailableRoomsForLandlord(landlordCode: String): List<RoomEntity> {
        return try {
            val snapshot = roomsCollection
                .whereEqualTo("landlordCode", landlordCode)
                .whereEqualTo("status", "可租")
                .get()
                .await()
            val rooms = snapshot.toObjects(RoomEntity::class.java)
            // 將最新的可租房間資訊更新到本地
            CoroutineScope(Dispatchers.IO).launch {
                roomDao.insertRooms(rooms)
            }
            rooms
        } catch (e: Exception) {
            Log.e("RoomRepository", "Error getting available rooms", e)
            // 網路失敗時，從本地讀取
            roomDao.getRoomsByLandlordCode(landlordCode).filter { it.status == "可租" }
        }
    }

    /**
     * [寫入]
     * 新增一筆房間資料。
     * 1. 先寫入 Firestore。
     * 2. 成功後，再寫入本地 Room。
     */
    suspend fun addRoom(room: RoomEntity) {
        val docRef = roomsCollection.document()
        val newRoom = room.copy(id = docRef.id)
        // 1. 操作雲端
        docRef.set(newRoom).await()
        // 2. 更新本地
        roomDao.insertRoom(newRoom)
    }

    /**
     * [修改]
     * 更新一筆房間資料。
     * 1. 先更新 Firestore。
     * 2. 成功後，再更新本地 Room。
     */
    suspend fun updateRoom(room: RoomEntity) {
        if (room.id.isBlank()) {
            throw IllegalArgumentException("Room ID cannot be blank for update.")
        }
        // 1. 操作雲端
        roomsCollection.document(room.id).set(room).await()
        // 2. 更新本地
        roomDao.insertRoom(room) // 使用 insert onConflict REPLACE 策略，達到更新效果
    }

    /**
     * [刪除]
     * 刪除一筆房間資料。
     * 1. 先從 Firestore 刪除。
     * 2. 成功後，再從本地 Room 刪除。
     */
    suspend fun deleteRoom(room: RoomEntity) {
        if (room.id.isBlank()) {
            throw IllegalArgumentException("Room ID cannot be blank for delete.")
        }
        // 1. 操作雲端
        roomsCollection.document(room.id).delete().await()
        // 2. 更新本地
        roomDao.deleteRoom(room)
    }
}