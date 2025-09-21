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

class RepairReportRepository(private val repairReportDao: RepairReportDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val reportsCollection = firestore.collection("repair_reports")

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

    // 【*** 修正：優化 insert 邏輯 ***】
    suspend fun insert(report: RepairReport) {
        val docRef = reportsCollection.document()
        docRef.set(report.copy(id = docRef.id)).await()
    }

    // 【*** 修正：在 RepairReport.id 改為 String 後，此處邏輯即可正常運作 ***】
    suspend fun update(report: RepairReport) {
        if (report.id.isBlank()) throw IllegalArgumentException("Report ID cannot be blank for update.")
        reportsCollection.document(report.id).set(report).await()
    }
}