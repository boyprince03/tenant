package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RoomRepository(private val roomDao: RoomDao, private val coroutineScope: CoroutineScope) {

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
                Log.e("RoomRepository", "Listen failed for landlord $landlordCode.", error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val rooms = snapshot.toObjects<RoomEntity>()
                coroutineScope.launch {
                    // 當雲端有變動，就用最新的資料替換本地的資料
                    roomDao.replaceRoomsForLandlord(landlordCode, rooms)
                }
            } // Removed the else block that would delete rooms on snapshot == null
        }
        // UI 永遠從本地 Room 讀取
        return roomDao.getRoomsByLandlordCodeFlow(landlordCode)
    }

    /**
     * [一次性讀取]
     * 從 Firestore 獲取特定房東的可租用房間。
     * 讀取後也會更新本地快取中對應的房間。
     * 資料始終從本地資料庫讀取。
     */
    suspend fun getAvailableRoomsForLandlord(landlordCode: String): List<RoomEntity> {
        try {
            val snapshot = roomsCollection
                .whereEqualTo("landlordCode", landlordCode)
                .whereEqualTo("status", "可租")
                .get()
                .await()
            val roomsFromFirestore = snapshot.toObjects(RoomEntity::class.java)
            // 將最新的可租房間資訊更新到本地 (只更新這些被拉取的房間)
            if (roomsFromFirestore.isNotEmpty()) {
                 coroutineScope.launch {
                    roomDao.insertRooms(roomsFromFirestore) // insertRooms uses OnConflictStrategy.REPLACE
                }.join() // Wait for cache update before reading from it
            }
        } catch (e: Exception) {
            Log.e("RoomRepository", "Error getting available rooms from Firestore for landlord $landlordCode", e)
            // 網路失敗時，不影響後續從本地讀取的操作
        }
        // 總是從本地讀取，無論 Firestore 是否成功
        return roomDao.getRoomsByLandlordCode(landlordCode).filter { it.status == "可租" }
    }

    /**
     * 【*** 新增此函式 ***】
     * [一次性讀取]
     * 從 Firestore 根據房號獲取單一房間資料，並更新本地快取。
     * 這確保了當使用者需要查看特定房間的詳細資訊時，能看到最新的資料。
     */
    suspend fun refreshRoomByNumber(roomNumber: String) {
        try {
            val snapshot = roomsCollection
                .whereEqualTo("roomNumber", roomNumber)
                .limit(1)
                .get()
                .await()
            val room = snapshot.documents.firstOrNull()?.toObject(RoomEntity::class.java)
            if (room != null) {
                // 使用 insertRoom 搭配 OnConflictStrategy.REPLACE 來達到更新效果
                roomDao.insertRoom(room)
            }
        } catch (e: Exception) {
            Log.e("RoomRepository", "Error refreshing room $roomNumber from Firestore", e)
            // 即使雲端抓取失敗，App 仍可顯示本地的舊資料，不會因此崩潰
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