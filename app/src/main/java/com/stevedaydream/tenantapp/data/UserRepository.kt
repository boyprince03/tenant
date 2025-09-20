package com.stevedaydream.tenantapp.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(private val userDao: UserDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    /**
     * 從 Firestore 取得特定使用者資料，並更新本地快取
     */
    suspend fun getUser(userId: String): User? {
        return try {
            val doc = usersCollection.document(userId).get().await()
            val user = doc.toObject(User::class.java)
            user?.let { userDao.insert(it) } // 更新本地快取
            user
        } catch (e: Exception) {
            // 網路失敗時，從本地快取讀取
            userDao.getUserById(userId)
        }
    }

    /**
     * 更新使用者資料 (雲端與本地)
     */
    suspend fun updateUser(user: User) {
        // 先更新 Firestore
        usersCollection.document(user.id).set(user).await()
        // 再更新本地
        userDao.updateUser(user)
    }

    /**
     * 透過房東序號查詢房東 (從 Firestore)
     */
    suspend fun getLandlordByCode(code: String): User? {
        val querySnapshot = usersCollection
            .whereEqualTo("landlordCode", code)
            .whereEqualTo("role", "landlord")
            .limit(1)
            .get()
            .await()
        return querySnapshot.documents.firstOrNull()?.toObject(User::class.java)
    }

    /**
     * 取得所有房東列表 (從 Firestore)
     */
    suspend fun getAllLandlords(): List<User> {
        val querySnapshot = usersCollection
            .whereEqualTo("role", "landlord")
            .get()
            .await()
        return querySnapshot.toObjects(User::class.java)
    }
}
