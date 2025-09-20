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
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * 取得特定房東的所有房間資料 (Flow)
     * 這會設定一個即時監聽器，自動同步雲端資料到本地
     */
    fun getRoomsForLandlord(landlordCode: String): Flow<List<RoomEntity>> {
        // 1. 設定 Firestore 即時監聽器
        roomsCollection.whereEqualTo("landlordCode", landlordCode)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RoomRepository", "Listen failed.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // 2. 將 snapshot 轉換為 RoomEntity 物件列表
                    val roomsFromFirestore = snapshot.toObjects<RoomEntity>()

                    // 3. 在背景執行緒中，將雲端資料更新到本地 Room 資料庫
                    scope.launch {
                        // 這裡使用 REPLACE 策略，會覆蓋所有現有資料，確保本地與雲端一致
                        roomDao.insertRooms(roomsFromFirestore)
                        Log.d("RoomRepository", "Synced ${roomsFromFirestore.size} rooms to local DB.")
                    }
                }
            }

        // 4. 回傳從本地 Room 資料庫讀取的 Flow，UI 會觀察這個 Flow
        return roomDao.getRoomsByLandlordCodeFlow(landlordCode)
    }

    /**
     * 新增一個房間
     */
    suspend fun addRoom(room: RoomEntity) {
        // 直接將物件寫入 Firestore，監聽器會自動處理後續的本地更新
        roomsCollection.document(room.id).set(room).await()
    }

    /**
     * 更新一個房間
     */
    suspend fun updateRoom(room: RoomEntity) {
        // Firestore 的 set 會覆蓋文件，效果等同於更新
        roomsCollection.document(room.id).set(room).await()
    }

    /**
     * 刪除一個房間
     */
    suspend fun deleteRoom(room: RoomEntity) {
        roomsCollection.document(room.id).delete().await()
    }
}
