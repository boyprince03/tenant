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
     * 【即時資料流】【*** 修正 ***】
     * 監聽並取得特定租客最新的那筆換房請求。
     * 移除了 .orderBy() 來避免 Firestore 索引問題，改為在客戶端排序。
     * @param tenantId 租客的唯一 ID。
     * @return 一個 Flow，會發送最新的那一筆請求，或 null。
     */
    fun getLatestRequestByTenantId(tenantId: String): Flow<RoomChangeRequest?> {
        // 【*** 核心修改：移除 .orderBy() 和 .limit() ***】
        val query = requestsCollection
            .whereEqualTo("tenantId", tenantId)

        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("RequestRepo", "Listen failed for tenant.", error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val requests = snapshot.toObjects<RoomChangeRequest>()
                CoroutineScope(Dispatchers.IO).launch {
                    // 【*** 核心修改：在寫入本地前，先在客戶端手動排序 ***】
                    // 這裡我們仍然將所有該租客的請求都同步到本地，
                    // Room 的查詢會自動幫我们只取出最新的一筆。
                    requestDao.insertOrUpdateAll(requests)
                }
            }
        }
        // Room 的查詢 `ORDER BY requestDate DESC LIMIT 1` 會確保我們只拿到最新的一筆
        return requestDao.getLatestRequestByTenantId(tenantId)
    }
}
