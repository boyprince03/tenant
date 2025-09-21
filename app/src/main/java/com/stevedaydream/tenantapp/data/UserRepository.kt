package com.stevedaydream.tenantapp.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val userDao: UserDao,
    private val coroutineScope: CoroutineScope
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    init {
        listenForAllLandlords()
    }

    /**
     * Creates or updates a user in Firestore and then in the local database.
     */
    suspend fun saveUser(user: User) {
        try {
            usersCollection.document(user.id).set(user).await()
            userDao.insert(user) // Uses OnConflictStrategy.REPLACE
        } catch (e: Exception) {
            Log.e("UserRepository", "Error saving user ${user.id}", e)
            // Optionally, rethrow or handle more gracefully
        }
    }

    /**
     * Retrieves a user by their ID from the local database as a Flow.
     * The local data is updated by Firestore listeners.
     * Call refreshUser(userId) to explicitly fetch from Firestore if needed.
     */
    fun getUser(userId: String): Flow<User?> {
        // Consider starting a specific listener if not already active,
        // or rely on a global listener or manual refresh.
        // For now, we assume a listener might be active or refreshUser is called.
        startSpecificUserListener(userId) // Ensure a listener is active for this user
        return userDao.getUserById(userId)
    }

    /**
     * Fetches a user's data from Firestore and updates the local cache.
     * The Flow returned by getUser(userId) will then emit the new data.
     */
    suspend fun refreshUser(userId: String) {
        try {
            val userDocument = usersCollection.document(userId).get().await()
            val user = userDocument.toObject(User::class.java)
            user?.let { userDao.insert(it) }
        } catch (e: Exception) {
            Log.e("UserRepository", "Error refreshing user $userId", e)
        }
    }

    /**
     * Retrieves a landlord by their unique code from the local database as a Flow.
     * The local data is updated by Firestore listeners.
     */
    fun getLandlordByCode(code: String): Flow<User?> {
        startLandlordByCodeListener(code) // Ensure listener is active
        return userDao.getLandlordByCode(code)
    }

    /**
     * Retrieves all landlords from the local database as a Flow.
     * The local data is kept in sync by a listener started in init.
     */
    fun getAllLandlords(): Flow<List<User>> {
        return userDao.getAllLandlords()
    }


    /**
     * 【*** 新增此函式 ***】
     * 從 Firestore 根據房東序號抓取房東的最新資料並更新本地快取。
     */
    suspend fun refreshLandlordByCode(code: String) {
        try {
            val snapshot = usersCollection
                .whereEqualTo("landlordCode", code)
                .whereEqualTo("role", "landlord")
                .limit(1)
                .get()
                .await()
            val landlord = snapshot.documents.firstOrNull()?.toObject(User::class.java)
            landlord?.let { userDao.insert(it) }
        } catch (e: Exception) {
            Log.e("UserRepository", "Error refreshing landlord with code $code", e)
        }
    }
    private fun listenForAllLandlords() {
        usersCollection.whereEqualTo("role", "landlord")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("UserRepository", "Error listening to all landlords", error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val landlords = it.toObjects<User>()
                    coroutineScope.launch {
                        userDao.insertOrUpdateAll(landlords)
                    }
                }
            }
    }

    private fun startSpecificUserListener(userId: String) {
        usersCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("UserRepository", "Error listening to user $userId", error)
                    return@addSnapshotListener
                }
                snapshot?.toObject(User::class.java)?.let { user ->
                    coroutineScope.launch {
                        userDao.insert(user)
                    }
                }
            }
    }

    private fun startLandlordByCodeListener(code: String) {
        usersCollection
            .whereEqualTo("landlordCode", code)
            .whereEqualTo("role", "landlord")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("UserRepository", "Error listening to landlord by code $code", error)
                    return@addSnapshotListener
                }
                snapshot?.let { querySnapshot ->
                    val users = querySnapshot.toObjects<User>()
                    // landlordCode should be unique for landlords, so users list should have 0 or 1 item.
                    // If more, all will be inserted/updated locally. DAO query still gets one.
                    if (users.isNotEmpty()) {
                         coroutineScope.launch {
                            userDao.insertOrUpdateAll(users)
                        }
                    }
                }
            }
    }
}
