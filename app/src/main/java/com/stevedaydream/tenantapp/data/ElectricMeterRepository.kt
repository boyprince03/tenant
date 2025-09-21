package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ElectricMeterRepository(
    private val meterDao: ElectricMeterDao,
    private val coroutineScope: CoroutineScope // Added CoroutineScope
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val recordsCollection = firestore.collection("electric_meter_records")

    /**
     * 【*** 新增此函式 ***】
     * 從 Firestore 拉取所有電費紀錄，並強制更新本地資料庫。
     * @return 回傳一個 Pair，包含操作是否成功 (Boolean) 和結果訊息 (String)。
     */
    suspend fun syncAllRecordsFromFirestore(): Pair<Boolean, String> {
        return try {
            val snapshot = recordsCollection.get().await()
            val records = snapshot.toObjects<ElectricMeterRecord>()
            meterDao.insertOrUpdateRecords(records)
            Pair(true, "成功同步 ${records.size} 筆電費紀錄！")
        } catch (e: Exception) {
            Log.e("ElectricMeterRepo", "Error syncing all records from Firestore", e)
            Pair(false, "同步失敗: ${e.message}")
        }
    }


    suspend fun insertOrUpdateRecords(records: List<ElectricMeterRecord>) {
        try {
            val batch = firestore.batch()
            records.forEach { record ->
                // Ensure ID is set for Firestore document ID and for the object itself
                val docId = record.id.ifBlank { "${record.roomNumber}_${record.recordMonth}" }
                val recordWithId = if (record.id.isBlank()) record.copy(id = docId) else record
                
                val docRef = recordsCollection.document(docId)
                batch.set(docRef, recordWithId)
            }
            batch.commit().await()
            // 同步更新本地數據庫
            coroutineScope.launch { // Use injected scope for DAO operations
                 meterDao.insertOrUpdateRecords(records) // Assuming records list already has IDs correctly set if needed by DAO
            }.join() // Wait for local update if consistency is critical immediately after
        } catch (e: Exception) {
            Log.e("ElectricMeterRepo", "Error inserting/updating records in Firestore", e)
            // Decide if we should still try to update local DAO or throw/handle error
            // For now, if Firestore fails, we might not want to update local with potentially stale/incomplete data.
            // However, the original code updated local DAO regardless, so keeping that pattern unless specified otherwise.
            // Consider re-throwing or specific error handling based on requirements.
            Log.d("ElectricMeterRepo", "Attempting to update local cache despite Firestore error for insertOrUpdateRecords")
            meterDao.insertOrUpdateRecords(records) // Original behavior: update local even if Firestore fails
        }
    }

    suspend fun getRecord(roomNo: String, month: String): ElectricMeterRecord? {
        val docId = "${roomNo}_${month}"
        try {
            // 1. Attempt to fetch from Firestore
            val recordFromFirestore = recordsCollection.document(docId).get().await().toObject(ElectricMeterRecord::class.java)
            
            if (recordFromFirestore != null) {
                // 2. If successful, update local cache
                coroutineScope.launch { 
                    meterDao.insertOrUpdateRecords(listOf(recordFromFirestore))
                }.join() // Wait for cache update
            } // If null, Firestore doesn't have it; local cache will be the source of truth if it exists there
        } catch (e: Exception) {
            Log.e("ElectricMeterRepo", "Error getting record $docId from Firestore. Will use local cache.", e)
            // Failure to fetch from Firestore, proceed to read from local cache
        }
        // 3. Always return from local cache
        return meterDao.getRecord(roomNo, month)
    }

    suspend fun getPreviousRecord(roomNo: String, month: String): ElectricMeterRecord? {
        // This logic remains DAO-centric due to Firestore query complexity for "previous"
        return meterDao.getPreviousRecord(roomNo, month)
    }
}