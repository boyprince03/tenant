package com.stevedaydream.tenantapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(private val userDao: UserDao) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    /**
     * 使用 Firebase 進行登入
     * @param email 使用者的電子郵件
     * @param password 使用者的密碼
     * @return 成功時回傳 User 物件，失敗時拋出例外
     */
    suspend fun login(email: String, password: String): User {
        // 1. 呼叫 Firebase Auth API 進行登入
        val authResult = auth.signInWithEmailAndPassword(email, password).await()
        val uid = authResult.user?.uid ?: throw Exception("登入失敗，找不到使用者 UID。")

        // 2. 使用 UID 從 Firestore 取得使用者詳細資料
        val userDoc = usersCollection.document(uid).get().await()
        val user = userDoc.toObject(User::class.java)
            ?: throw Exception("在 Firestore 中找不到對應的使用者資料。")

        // 3. 登入成功後，將從雲端取得的最新資料，更新到本機 Room 資料庫中
        userDao.insert(user) // 使用我們之前設定的 OnConflictStrategy.REPLACE
        return user
    }

    /**
     * 使用 Firebase 進行註冊
     * @param user 包含使用者基本資料的 User 物件 (除了 id)
     * @param email 註冊用的電子郵件
     * @param password 註冊用的密碼
     * @return 成功時回傳包含新 UID 的 User 物件，失敗時拋出例外
     */
    suspend fun register(user: User, email: String, password: String): User {
        // 1. 呼叫 Firebase Auth API 建立新帳號
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = authResult.user?.uid ?: throw Exception("註冊失敗，無法取得 UID。")

        // 2. 將 Firebase 回傳的 UID 寫入我們的 User 物件
        val newUser = user.copy(id = uid)

        // 3. 將完整的使用者資料寫入 Firestore，文件 ID 就是使用者的 UID
        usersCollection.document(uid).set(newUser).await()
        return newUser
    }

    /**
     * 使用 Google 帳號的 ID Token 進行登入或註冊
     * @param idToken 從 Google 登入流程中獲取的 ID Token
     * @return 成功時回傳 User 物件，失敗時拋出例外
     */
    suspend fun loginWithGoogle(idToken: String): User {
        // 1. 根據 idToken 建立 Firebase 的 Google 憑證
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        // 2. 使用憑證登入 Firebase
        val authResult = auth.signInWithCredential(credential).await()
        val firebaseUser = authResult.user ?: throw Exception("Google 登入失敗，無法取得 Firebase 使用者。")

        // 3. 檢查這是新使用者還是舊使用者
        val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false

        val user: User
        if (isNewUser) {
            // 4a. 如果是新使用者，從 FirebaseUser 取得基本資料，並在 Firestore 中建立新文件
            val newUser = User(
                id = firebaseUser.uid,
                username = firebaseUser.displayName ?: "未命名",
                phone = firebaseUser.phoneNumber ?: "",
                role = "tenant" // 預設新註冊者為租客
            )
            usersCollection.document(firebaseUser.uid).set(newUser).await()
            user = newUser
        } else {
            // 4b. 如果是舊使用者，直接從 Firestore 讀取現有資料
            val userDoc = usersCollection.document(firebaseUser.uid).get().await()
            user = userDoc.toObject(User::class.java)
                ?: throw Exception("在 Firestore 中找不到對應的使用者資料。")
        }

        // 5. 將最新的使用者資料（無論新舊）更新到本地 Room 資料庫
        userDao.insert(user)
        return user
    }

    /**
     * 登出
     */
    fun logout() {
        auth.signOut()
        // 登出時，您可以選擇性地清除本機快取的資料
    }

    /**
     * 取得目前登入中的 Firebase 使用者
     */
    fun getCurrentFirebaseUser() = auth.currentUser
}