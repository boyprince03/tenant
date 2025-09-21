package com.stevedaydream.tenantapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

        // DAOs for local DB access
        val roomDao = db.roomDao()
        val announcementDao = db.announcementDao()
        val repairReportDao = db.repairReportDao()

        // Repositories for cloud access
        val roomRepository = RoomRepository(roomDao)
        val announcementRepository = AnnouncementRepository(announcementDao)
        val repairReportRepository = RepairReportRepository(repairReportDao)

        // 【*** 核心修改 1：為所有預設資料預先產生 ID ***】
        // RoomEntity 會在建構時自動產生 UUID
        val defaultRooms = listOf(
            RoomEntity(roomNumber = "401", status = "可租", type="雅房", rentAmount = 6000, deposit=12000),
            RoomEntity(roomNumber = "402", status = "可租", type="套房", rentAmount = 8500, deposit=17000),
            RoomEntity(roomNumber = "403", status = "可租", type="雅房", rentAmount = 5500, deposit=11000),
            RoomEntity(roomNumber = "501", status = "可租", type="套房", rentAmount = 9000, deposit=18000),
            RoomEntity(roomNumber = "502", status = "可租", type="雅房", rentAmount = 6200, deposit=12400),
            RoomEntity(roomNumber = "503", status = "可租", type="家庭式", rentAmount = 15000, deposit=30000),
            RoomEntity(roomNumber = "504", status = "可租", type="雅房", rentAmount = 5800, deposit=11600)
        )

        val defaultAnnouncements = listOf(
            Announcement(id = UUID.randomUUID().toString(), title = "中秋節快樂", content = "祝各位住戶中秋佳節愉快，月圓人團圓！"),
            Announcement(id = UUID.randomUUID().toString(), title = "停水通知", content = "本週三(09/25)早上9點至下午1點因洗水塔將暫停供水，請提早儲水備用。"),
            Announcement(id = UUID.randomUUID().toString(), title = "公共區域清潔", content = "每週一、四將有專人進行公共區域的清潔與消毒，請保持走道暢通。")
        )

        val defaultRepairReports = listOf(
            RepairReport(id = UUID.randomUUID().toString(), tenantName = "張三", roomNumber = "401", issue = "電燈不亮", description = "房間主燈的燈泡燒掉了，麻煩協助更換。", status = "處理中"),
            RepairReport(id = UUID.randomUUID().toString(), tenantName = "李四", roomNumber = "402", issue = "水龍頭漏水", description = "浴室洗手台的水龍頭關不緊，一直在滴水。")
        )

        // 【*** 核心修改 2：修改同步邏輯 ***】
        CoroutineScope(Dispatchers.IO).launch {
            val firestore = FirebaseFirestore.getInstance()
            // 檢查雲端資料庫是否為空
            val cloudRoomCount = firestore.collection("rooms").limit(1).get().await().size()

            if (cloudRoomCount == 0) {
                Log.d("MainActivity", "Firestore is empty. Seeding both Firestore and local Room DB.")

                // 步驟 1: 使用 Repository 的 update 方法將資料（包含預設ID）推送到 Firestore
                Log.d("MainActivity", "Uploading to Firestore...")
                defaultRooms.forEach { roomRepository.updateRoom(it) }
                defaultAnnouncements.forEach { announcementRepository.update(it) }
                defaultRepairReports.forEach { repairReportRepository.update(it) }
                Log.d("MainActivity", "Firestore upload complete.")

                // 步驟 2: 使用 DAO 將相同的資料寫入本地 Room 資料庫
                Log.d("MainActivity", "Writing to local Room DB...")
                roomDao.insertRooms(defaultRooms)
                announcementDao.insertOrUpdateAll(defaultAnnouncements)
                repairReportDao.insertOrUpdateAll(defaultRepairReports)
                Log.d("MainActivity", "Local Room DB write complete.")

            } else {
                Log.d("MainActivity", "Firestore already contains data. Skipping test data seeding.")
            }

            // 建立預設管理員帳號的邏輯不變
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
                // 檢查 Firestore 中是否已存在該使用者
                val existingUser = usersCollection.whereEqualTo("username", username).get().await()
                if (existingUser.isEmpty) {
                    // 1. 在 Firebase Auth 中建立帳號
                    val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                    val uid = authResult.user?.uid ?: continue

                    // 2. 在 Firestore 中建立對應的使用者資料
                    val adminUser = User(
                        id = uid,
                        username = username,
                        role = "admin"
                    )
                    usersCollection.document(uid).set(adminUser).await()
                    Log.d("AdminInit", "成功建立管理員: $username")
                } else {
                    Log.d("AdminInit", "管理員 $username 已存在")
                }
            } catch (e: Exception) {
                // 如果帳號已在 Auth 中但 Firestore 沒有，可能會拋出例外，這裡忽略
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
                // 清除 intent extra，避免重複導航
                intent.removeExtra("navigateTo")
                intent.removeExtra("landlordId")
            }
        }
    }
}