// tenantapp/data/RoomChangeRequestRepository.kt

package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RoomChangeRequestRepository(private val requestDao: RoomChangeRequestDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val requestsCollection = firestore.collection("room_change_requests")

    /**
     * [寫入]
     * 新增一筆換房請求。
     */
    suspend fun insert(request: RoomChangeRequest) {
        // 1. 操作雲端 (物件在建立時已產生 UUID)
        requestsCollection.document(request.id).set(request).await()
        // 2. 更新本地
        requestDao.insert(request)
    }

    /**
     * [修改]
     * 更新一筆換房請求 (例如：審核通過/拒絕)。
     */
    suspend fun update(request: RoomChangeRequest) {
        // 1. 操作雲端
        requestsCollection.document(request.id).set(request).await()
        // 2. 更新本地
        requestDao.update(request)
    }

    /**
     * 【即時資料流】
     * 監聽並取得特定房東的所有換房請求。
     * @param landlordCode 房東的唯一序號。
     * @return 一個 Flow，會持續發送最新的請求列表。
     */
    fun getRequestsByLandlord(landlordCode: String): Flow<List<RoomChangeRequest>> {
        val query = requestsCollection.whereEqualTo("landlordCode", landlordCode)

        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("RequestRepo", "Listen failed.", error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val requests = snapshot.toObjects<RoomChangeRequest>()
                CoroutineScope(Dispatchers.IO).launch {
                    requestDao.insertOrUpdateAll(requests)
                }
            }
        }
        // UI 永遠從本地 Room 讀取，以支援離線並加速顯示
        return requestDao.getRequestsByLandlord(landlordCode)
    }

    /**
     * 【即時資料流】
     * 監聽並取得特定租客最新的那筆換房請求。
     * @param tenantId 租客的唯一 ID。
     * @return 一個 Flow，會發送最新的那一筆請求，或 null。
     */
    fun getLatestRequestByTenantId(tenantId: String): Flow<RoomChangeRequest?> {
        val query = requestsCollection
            .whereEqualTo("tenantId", tenantId)
            .orderBy("requestDate", Query.Direction.DESCENDING)
            .limit(1)

        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("RequestRepo", "Listen failed for tenant.", error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val requests = snapshot.toObjects<RoomChangeRequest>()
                CoroutineScope(Dispatchers.IO).launch {
                    // 即使只有一筆，也用 List 的方式更新，確保資料庫操作一致
                    requestDao.insertOrUpdateAll(requests)
                }
            }
        }
        return requestDao.getLatestRequestByTenantId(tenantId)
    }
}