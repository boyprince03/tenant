package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val coroutineScope: CoroutineScope
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val paymentsCollection = firestore.collection("payments")

    init {
        listenForAllPayments()
    }

    private fun listenForAllPayments() {
        paymentsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("PaymentRepo", "Listen failed for all payments.", error)
                return@addSnapshotListener
            }
            snapshot?.let {
                val payments = it.toObjects<Payment>()
                coroutineScope.launch {
                    paymentDao.insertOrUpdateAll(payments)
                }
            }
        }
    }

    fun getPaymentRecord(roomNo: String, month: String): Flow<Payment?> {
        return paymentDao.getPaymentRecord(roomNo, month)
    }

    /**
     * Inserts or updates a payment record in Firestore and then in the local DAO.
     */
    suspend fun insertOrUpdate(payment: Payment) {
        val docId = payment.id.ifBlank { "${payment.roomNumber}_${payment.recordMonth}" }
        val paymentWithId = if (payment.id.isBlank()) payment.copy(id = docId) else payment

        try {
            paymentsCollection.document(docId).set(paymentWithId).await()
            coroutineScope.launch {
                paymentDao.insertOrUpdate(paymentWithId)
            }.join()
        } catch (e: Exception) {
            Log.e("PaymentRepo", "Error inserting/updating payment $docId in Firestore", e)
            throw e
        }
    }

    /**
     * 【*** 新增此方法 ***】
     * 處理付款確認，包括更新 isPaid 狀態。
     */
    suspend fun updatePaymentStatus(payment: Payment) {
        if (payment.id.isBlank()) throw IllegalArgumentException("Payment ID cannot be blank for update.")
        try {
            paymentsCollection.document(payment.id).set(payment).await()
            coroutineScope.launch {
                paymentDao.insertOrUpdate(payment)
            }.join()
        } catch (e: Exception) {
            Log.e("PaymentRepo", "Error updating payment status for ${payment.id}", e)
            throw e
        }
    }
}