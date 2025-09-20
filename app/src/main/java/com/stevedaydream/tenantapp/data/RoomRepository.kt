package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * RoomRepository (房間資料倉儲)
 *
 * 職責: 作為房間資料的中介層，協調雲端 Firestore 和本地 Room 資料庫。
 * UI 層只會跟這個 Repository 互動，而不會直接存取 DAO 或 Firestore。
 */
class RoomRepository(private val roomDao: RoomDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val roomsCollection = firestore.collection("rooms")

    /**
     * 【即時資料流】
     * 取得特定房東的所有房間資料，並即時監聽雲端變化。
     *
     * @param landlordCode 房東的唯一序號。
     * @return 一個 Flow，會持續發送最新的房間列表。
     */
    fun getRoomsForLandlord(landlordCode: String): Flow<List<RoomEntity>> {
        // 1. 建立一個對 Firestore 的查詢，篩選出屬於這個房東的房間
        val query = roomsCollection.whereEqualTo("landlordCode", landlordCode)

        // 2. 附加一個快照監聽器 (snapshotListener)。
        //    這個監聽器會在資料第一次被讀取，或雲端資料有任何變動時被觸發。
        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // 如果發生錯誤，可以在這裡處理，例如 log 下來
                Log.e("RoomRepository", "Listen failed.", error)
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                // 3. 將查詢到的文件轉換成 RoomEntity 物件列表
                val rooms = snapshot.toObjects<RoomEntity>()
                // 4. 在背景協程中，將最新的資料寫入本地 Room 資料庫
                //    使用 OnConflictStrategy.REPLACE，會自動覆蓋舊資料
                CoroutineScope(Dispatchers.IO).launch {
                    roomDao.insertRooms(rooms)
                }
            } else {
                // 雲端沒有資料，可以選擇性地清空本地資料
                CoroutineScope(Dispatchers.IO).launch {
                    // 確保只刪除該房東的房間
                    roomDao.deleteRoomsByLandlordCode(landlordCode)
                }
            }
        }

        // 5. 回傳一個從本地 Room 資料庫讀取的 Flow。
        //    UI 永遠是從本地快取讀取，所以速度快且支援離線。
        //    因為上面的監聽器會一直更新本地資料庫，所以這個 Flow 會自動反映最新的雲端狀態。
        return roomDao.getRoomsByLandlordCodeFlow(landlordCode)
    }

    /**
     * 【一次性讀取】
     * 從 Firestore 獲取特定房東名下所有狀態為 "可租" 的房間。
     *
     * @param landlordCode 房東的唯一序號。
     * @return 一個包含可租房間的 List。
     */
    suspend fun getAvailableRoomsForLandlord(landlordCode: String): List<RoomEntity> {
        return try {
            val snapshot = roomsCollection
                .whereEqualTo("landlordCode", landlordCode)
                .whereEqualTo("status", "可租") // 只查詢狀態為 "可租" 的房間
                .get()
                .await()
            snapshot.toObjects(RoomEntity::class.java)
        } catch (e: Exception) {
            Log.e("RoomRepository", "Error getting available rooms", e)
            emptyList() // 發生錯誤時回傳空列表
        }
    }

    /**
     * 【寫入操作】
     * 新增一筆房間資料到 Firestore。
     *
     * @param room 要新增的房間物件 (id 應為空)。
     */
    suspend fun addRoom(room: RoomEntity) {
        // 1. 讓 Firestore 自動產生一個唯一的文件 ID
        val docRef = roomsCollection.document()
        // 2. 將這個 ID 寫入我們的物件中，並將整個物件存入 Firestore
        docRef.set(room.copy(id = docRef.id)).await()
    }

    /**
     * 【寫入操作】
     * 更新一筆已存在的房間資料到 Firestore。
     *
     * @param room 包含更新後資料的房間物件 (id 必須是有效的)。
     */
    suspend fun updateRoom(room: RoomEntity) {
        if (room.id.isBlank()) {
            throw IllegalArgumentException("Room ID cannot be blank for update.")
        }
        roomsCollection.document(room.id).set(room).await()
    }

    /**
     * 【寫入操作】
     * 從 Firestore 刪除一筆房間資料。
     *
     * @param room 要刪除的房間物件 (id 必須是有效的)。
     */
    suspend fun deleteRoom(room: RoomEntity) {
        if (room.id.isBlank()) {
            throw IllegalArgumentException("Room ID cannot be blank for delete.")
        }
        roomsCollection.document(room.id).delete().await()
    }
}

