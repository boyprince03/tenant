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

        // DAOs for local DB access
        val roomDao = db.roomDao()
        val announcementDao = db.announcementDao()
        val repairReportDao = db.repairReportDao()
        val meterDao = db.electricMeterDao()
        val paymentDao = db.paymentDao()
        val userDao = db.userDao()
        val requestDao = db.roomChangeRequestDao()


        // --- 【*** 核心修正 1：建立 Repository 時傳入正確的 lifecycleScope ***】 ---
        // Repositories for cloud access
        val roomRepository = RoomRepository(roomDao, lifecycleScope)
        val announcementRepository = AnnouncementRepository(announcementDao, lifecycleScope)
        val repairReportRepository = RepairReportRepository(repairReportDao, lifecycleScope) // 傳入 scope
        val meterRepository = ElectricMeterRepository(meterDao, lifecycleScope)
        val paymentRepository = PaymentRepository(paymentDao, lifecycleScope)
        val userRepository = UserRepository(userDao, lifecycleScope)
        val requestRepository = RoomChangeRequestRepository(requestDao)
        val adminRepository = AdminRepository(userDao, roomDao, lifecycleScope)


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

        CoroutineScope(Dispatchers.IO).launch {
            val firestore = FirebaseFirestore.getInstance()
            val cloudRoomCount = firestore.collection("rooms").limit(1).get().await().size()

            if (cloudRoomCount == 0) {
                Log.d("MainActivity", "Firestore is empty. Seeding both Firestore and local Room DB.")

                // --- 【*** 核心修正 2：使用 insert 方法來新增初始資料，而非 update ***】 ---
                // update 會在本地資料庫找不到項目時失敗，insert 才能正確寫入新資料。
                Log.d("MainActivity", "Uploading to Firestore and local DB...")
                defaultRooms.forEach { roomRepository.addRoom(it) } // 使用 addRoom
                defaultAnnouncements.forEach { announcementRepository.insert(it) } // 使用 insert
                defaultRepairReports.forEach { repairReportRepository.insert(it) } // 使用 insert
                Log.d("MainActivity", "Seeding complete.")

            } else {
                Log.d("MainActivity", "Firestore already contains data. Skipping test data seeding.")
            }

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
                    Log.d("AdminInit", "管理員 $username 已存在")
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
