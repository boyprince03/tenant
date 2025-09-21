package com.stevedaydream.tenantapp.data

import android.content.Context // 【*** 新增 import ***】
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID // 【*** 新增 import ***】

// 【*** 新增一個資料類別，用來包裝 Google 登入的回傳結果 ***】
data class GoogleSignInResult(
    val user: User,
    val isNewUser: Boolean
)

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
        val authResult = auth.signInWithEmailAndPassword(email, password).await()
        val uid = authResult.user?.uid ?: throw Exception("登入失敗，找不到使用者 UID。")
        val userDoc = usersCollection.document(uid).get().await()
        val user = userDoc.toObject(User::class.java)
            ?: throw Exception("在 Firestore 中找不到對應的使用者資料。")
        userDao.insert(user) // Write to local Room
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
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = authResult.user?.uid ?: throw Exception("註冊失敗，無法取得 UID。")
        val newUser = user.copy(id = uid)
        // 1. Write to Firestore
        usersCollection.document(uid).set(newUser).await()
        // 2. Write to local Room database
        userDao.insert(newUser) 
        return newUser
    }

    /**
     * 【*** 核心修改：修改此函式的邏輯和回傳值 ***】
     * 使用 Google 帳號的 ID Token 進行登入或註冊的第一階段
     * @param idToken 從 Google 登入流程中獲取的 ID Token
     * @return 回傳 GoogleSignInResult 物件，包含使用者資訊及是否為新使用者
     */
    suspend fun loginWithGoogle(idToken: String): GoogleSignInResult {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(credential).await()
        val firebaseUser = authResult.user ?: throw Exception("Google 登入失敗，無法取得 Firebase 使用者。")

        val isNewUser = authResult.additionalUserInfo?.isNewUser ?: false

        if (isNewUser) {
            // 如果是新使用者，只建立一個暫時的 User 物件，還不要存到 Firestore
            val tempUser = User(
                id = firebaseUser.uid,
                username = firebaseUser.displayName ?: "未命名",
                phone = firebaseUser.phoneNumber ?: ""
                // 此時 role 尚未決定
            )
            // 回傳這個暫時的 user 物件，並標記為新使用者
            return GoogleSignInResult(user = tempUser, isNewUser = true)
        } else {
            // 如果是舊使用者，從 Firestore 讀取完整資料
            val userDoc = usersCollection.document(firebaseUser.uid).get().await()
            val existingUser = userDoc.toObject(User::class.java)
                ?: throw Exception("在 Firestore 中找不到對應的使用者資料。")

            // 將最新的資料更新到本地 Room
            userDao.insert(existingUser)
            // 回傳完整的使用者資料，並標記為非新使用者
            return GoogleSignInResult(user = existingUser, isNewUser = false)
        }
    }

    /**
     * 【*** 新增此函式：完成 Google 註冊的第二階段 ***】
     * 將選擇完角色的使用者資料寫入 Firestore 和 Room
     * @param userToSave 包含完整角色資訊的 User 物件
     */
    suspend fun completeGoogleRegistration(userToSave: User) {
        // 寫入 Firestore
        usersCollection.document(userToSave.id).set(userToSave).await()
        // 寫入本地 Room
        userDao.insert(userToSave)
    }


    /**
     * 【*** 核心修改：修改此函式 ***】
     * 登出 Firebase 和 Google Sign-In Client
     * @param context Android Context，用於取得 GoogleSignInClient
     */
    fun logout(context: Context) {
        // 1. 登出 Firebase
        auth.signOut()

        // 2. 登出 Google Sign-In Client
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInClient.signOut()
    }

    fun getCurrentFirebaseUser() = auth.currentUser
}
