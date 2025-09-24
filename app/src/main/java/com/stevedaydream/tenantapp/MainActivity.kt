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

// 【*** 新增：Firebase App Check 的相關導入 ***】
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)

        // 【*** 修正點 1：在此處初始化 Firebase App Check ***】
        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        CoroutineScope(Dispatchers.IO).launch {
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
                val existingUser = usersCollection.whereEqualTo("username", username).get().await()
                if (existingUser.isEmpty) {
                    val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                    val uid = authResult.user?.uid ?: continue
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
                intent.removeExtra("navigateTo")
                intent.removeExtra("landlordId")
            }
        }
    }
}