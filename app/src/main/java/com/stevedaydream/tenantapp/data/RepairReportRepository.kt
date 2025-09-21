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
import java.util.UUID

class RepairReportRepository(private val repairReportDao: RepairReportDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val reportsCollection = firestore.collection("repair_reports")

    /**
     * [讀取]
     * 取得所有回報的資料流，並監聽雲端變化。
     */
    fun getAllReports(): Flow<List<RepairReport>> {
        reportsCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RepairReportRepo", "Listen failed.", error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val reports = it.toObjects<RepairReport>()
                    CoroutineScope(Dispatchers.IO).launch {
                        repairReportDao.insertOrUpdateAll(reports)
                    }
                }
            }
        return repairReportDao.getAll()
    }

    /**
     * [寫入]
     * 新增一筆修繕回報。
     */
    suspend fun insert(report: RepairReport) {
        val docRef = reportsCollection.document()
        val newReport = report.copy(id = docRef.id)
        // 1. 操作雲端
        docRef.set(newReport).await()
        // 2. 更新本地
        repairReportDao.insert(newReport)
    }

    /**
     * [修改]
     * 更新一筆修繕回報 (例如：更新狀態)。
     */
    suspend fun update(report: RepairReport) {
        if (report.id.isBlank()) throw IllegalArgumentException("Report ID cannot be blank for update.")
        // 1. 操作雲端
        reportsCollection.document(report.id).set(report).await()
        // 2. 更新本地
        repairReportDao.update(report)
    }
}