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
    private val coroutineScope: CoroutineScope // Injected CoroutineScope
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val paymentsCollection = firestore.collection("payments")

    init {
        listenForAllPayments()
    }

    private fun listenForAllPayments() {
        // General listener for all payments to keep local cache updated.
        // Consider adding .orderBy() if a specific order is beneficial for caching or initial display.
        paymentsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("PaymentRepo", "Listen failed for all payments.", error)
                return@addSnapshotListener
            }
            snapshot?.let {
                val payments = it.toObjects<Payment>()
                coroutineScope.launch { // Use the injected scope
                    paymentDao.insertOrUpdateAll(payments)
                }
            }
        }
    }

    /**
     * Gets a payment record Flow from the local DAO.
     * The local DAO is kept in sync by the Firestore listener in the init block.
     */
    fun getPaymentRecord(roomNo: String, month: String): Flow<Payment?> {
        return paymentDao.getPaymentRecord(roomNo, month)
    }

    /**
     * Inserts or updates a payment record in Firestore and then in the local DAO.
     */
    suspend fun insertOrUpdate(payment: Payment) {
        // Ensure a consistent ID for Firestore document and local entity
        val docId = payment.id.ifBlank { "${payment.roomNumber}_${payment.recordMonth}" }
        val paymentWithId = if (payment.id.isBlank()) payment.copy(id = docId) else payment

        try {
            // 1. Write to Firestore
            paymentsCollection.document(docId).set(paymentWithId).await()
            // 2. Write to local DAO
            coroutineScope.launch { // Use injected scope for DAO operation
                paymentDao.insertOrUpdate(paymentWithId) // Assumes OnConflictStrategy.REPLACE
            }.join() // Optional: wait for DAO operation if immediate consistency is needed
        } catch (e: Exception) {
            Log.e("PaymentRepo", "Error inserting/updating payment $docId in Firestore", e)
            // Consider re-throwing or specific error handling based on requirements.
            // For now, if Firestore fails, the local cache won't be updated with this specific item.
            throw e // Re-throw to make the caller aware of the failure
        }
    }
}