package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PaymentRepository(private val paymentDao: PaymentDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val paymentsCollection = firestore.collection("payments")

    fun getPaymentRecord(roomNo: String, month: String): Flow<Payment?> {
        paymentsCollection
            .whereEqualTo("roomNumber", roomNo)
            .whereEqualTo("recordMonth", month)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PaymentRepo", "Listen failed.", error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val payments = it.toObjects<Payment>()
                    CoroutineScope(Dispatchers.IO).launch {
                        paymentDao.insertOrUpdateAll(payments)
                    }
                }
            }
        return paymentDao.getPaymentRecord(roomNo, month)
    }

    suspend fun insertOrUpdate(payment: Payment) {
        // 使用 roomNumber 和 recordMonth 作為複合主鍵
        val docId = "${payment.roomNumber}_${payment.recordMonth}"
        paymentsCollection.document(docId).set(payment.copy(id = docId)).await()
    }
}