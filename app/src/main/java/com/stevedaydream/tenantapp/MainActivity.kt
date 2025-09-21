package com.stevedaydream.tenantapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.stevedaydream.tenantapp.data.*
import com.stevedaydream.tenantapp.navigation.AppNavGraph
import com.stevedaydream.tenantapp.ui.theme.TenantAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)

        // --- 【*** 移除所有測試資料的 List ***】 ---

        CoroutineScope(Dispatchers.IO).launch {
            // 只保留建立預設 admin 帳號的邏輯
            createDefaultAdmins()
        }

        setContent {
            TenantAppTheme {
                val navController = rememberNavController()
                AppNavGraph(navController, db)

                LaunchedEffect(intent) {
                    handleNotificationIntent(intent, navController)
                }
            }
        }
    }

    private suspend fun createDefaultAdmins() {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val usersCollection = firestore.collection("users")

        for (i in 1..3) {
            val adminIndex = "00$i"
            val email = "admin$adminIndex@example.com"
            val password = "${adminIndex}admin$adminIndex"
            val username = "admin$adminIndex"

            try {
                // 檢查 Firestore 中是否已存在該用戶名的管理員
                val existingUser = usersCollection.whereEqualTo("username", username).get().await()
                if (existingUser.isEmpty) {
                    // 1. 在 Firebase Auth 建立帳號
                    val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                    val uid = authResult.user?.uid ?: continue // 如果 uid 為空則跳過

                    // 2. 在 Firestore 的 users 集合中建立對應的使用者文件
                    val adminUser = User(
                        id = uid,
                        username = username,
                        role = "admin"
                    )
                    usersCollection.document(uid).set(adminUser).await()
                    Log.d("AdminInit", "成功建立管理員: $username")
                } else {
                    Log.d("AdminInit", "管理員 $username 已存在，跳過建立。")
                }
            } catch (e: Exception) {
                // 如果帳號已存在於 Auth 但不存在於 Firestore，可能會拋出例外
                Log.e("AdminInit", "建立管理員 $username 失敗: ${e.message}")
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?, navController: NavHostController) {
        if (intent == null) return

        val navigateTo = intent.getStringExtra("navigateTo")
        if (navigateTo == "room_change_approval") {
            val landlordId = intent.getStringExtra("landlordId")
            if (!landlordId.isNullOrBlank()) {
                navController.navigate("room_change_approval/$landlordId")
                // 清除 intent 中的資料，避免重複導航
                intent.removeExtra("navigateTo")
                intent.removeExtra("landlordId")
            }
        }
    }
}