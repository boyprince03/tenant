package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow

class RepairReportRepository(
    private val repairReportDao: RepairReportDao,
    private val coroutineScope: CoroutineScope // Injected CoroutineScope
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val reportsCollection = firestore.collection("repair_reports")

    init {
        listenForAllReports()
    }

    private fun listenForAllReports() {
        reportsCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RepairReportRepo", "Listen failed for all reports.", error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val reports = it.toObjects<RepairReport>()
                    coroutineScope.launch { // Use injected scope
                        repairReportDao.insertOrUpdateAll(reports)
                    }
                }
            }
    }

    /**
     * [讀取]
     * 取得所有回報的資料流從本地 DAO。
     * 本地 DAO 由 Firestore 監聽器在 init 區塊中保持同步。
     */
    fun getAllReports(): Flow<List<RepairReport>> {
        return repairReportDao.getAll()
    }

    /**
     * [寫入]
     * 新增一筆修繕回報到 Firestore 並更新本地 DAO。
     */
    suspend fun insert(report: RepairReport) {
        val docRef = reportsCollection.document() // Firestore generates ID
        val newReport = report.copy(id = docRef.id)
        try {
            // 1. 操作雲端
            docRef.set(newReport).await()
            // 2. 更新本地
            coroutineScope.launch {
                repairReportDao.insert(newReport) // Assumes OnConflictStrategy.REPLACE
            }.join() // Optional: wait for DAO operation
        } catch (e: Exception) {
            Log.e("RepairReportRepo", "Error inserting report ${newReport.id} into Firestore", e)
            throw e // Re-throw to make the caller aware
        }
    }

    /**
     * [修改]
     * 更新一筆修繕回報 (例如：更新狀態) 到 Firestore 並更新本地 DAO。
     */
    suspend fun update(report: RepairReport) {
        if (report.id.isBlank()) throw IllegalArgumentException("Report ID cannot be blank for update.")
        try {
            // 1. 操作雲端
            reportsCollection.document(report.id).set(report).await()
            // 2. 更新本地
            coroutineScope.launch {
                repairReportDao.update(report) // Assumes OnConflictStrategy.REPLACE implicitly or explicitly
            }.join() // Optional: wait for DAO operation
        } catch (e: Exception) {
            Log.e("RepairReportRepo", "Error updating report ${report.id} in Firestore", e)
            throw e // Re-throw to make the caller aware
        }
    }
}