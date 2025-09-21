package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ElectricMeterRepository(private val meterDao: ElectricMeterDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val recordsCollection = firestore.collection("electric_meter_records")

    suspend fun insertOrUpdateRecords(records: List<ElectricMeterRecord>) {
        val batch = firestore.batch()
        records.forEach { record ->
            val docId = "${record.roomNumber}_${record.recordMonth}"
            val docRef = recordsCollection.document(docId)
            batch.set(docRef, record.copy(id = docId))
        }
        batch.commit().await()
        // 同步更新本地數據庫
        meterDao.insertOrUpdateRecords(records)
    }

    suspend fun getRecord(roomNo: String, month: String): ElectricMeterRecord? {
        return try {
            val docId = "${roomNo}_${month}"
            recordsCollection.document(docId).get().await().toObject(ElectricMeterRecord::class.java)
        } catch (e: Exception) {
            Log.e("ElectricMeterRepo", "Error getting record", e)
            meterDao.getRecord(roomNo, month) // 從本地讀取
        }
    }

    suspend fun getPreviousRecord(roomNo: String, month: String): ElectricMeterRecord? {
        // 注意：這個邏輯在 Firestore 中較難實現，建議在 ViewModel 中處理月份計算
        // 這裡我們仍然從本地 Room 獲取上個月紀錄
        return meterDao.getPreviousRecord(roomNo, month)
    }
}