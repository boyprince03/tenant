package com.stevedaydream.tenantapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.navigation.AppNavGraph
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.ui.theme.TenantAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.stevedaydream.tenantapp.data.Announcement
import com.stevedaydream.tenantapp.data.RepairReport
import kotlinx.coroutines.flow.firstOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val roomDao = db.roomDao()
        // --- 【核心修改：取得 DAO】 ---
        val announcementDao = db.announcementDao()
        val repairReportDao = db.repairReportDao()

        // 默认房间资料（保持不变）
        val defaultRooms = listOf(
            RoomEntity(roomNumber = "401", status = "可租"),
            RoomEntity(roomNumber = "402", status = "可租"),
            RoomEntity(roomNumber = "403", status = "可租"),
            RoomEntity(roomNumber = "501", status = "可租"),
            RoomEntity(roomNumber = "502", status = "可租"),
            RoomEntity(roomNumber = "503", status = "可租"),
            RoomEntity(roomNumber = "504", status = "可租")
        )

        // --- 【核心修改：建立預設公告】 ---
        val defaultAnnouncements = listOf(
            Announcement(title = "中秋節快樂", content = "祝各位住戶中秋佳節愉快，月圓人團圓！"),
            Announcement(title = "停水通知", content = "本週三(09/25)早上9點至下午1點因洗水塔將暫停供水，請提早儲水備用。"),
            Announcement(title = "公共區域清潔", content = "每週一、四將有專人進行公共區域的清潔與消毒，請保持走道暢通。")
        )

        // --- 【核心修改：建立預設報修】 ---
        val defaultRepairReports = listOf(
            RepairReport(tenantName = "張三", roomNumber = "401", issue = "電燈不亮", description = "房間主燈的燈泡燒掉了，麻煩協助更換。", status = "處理中"),
            RepairReport(tenantName = "李四", roomNumber = "402", issue = "水龍頭漏水", description = "浴室洗手台的水龍頭關不緊，一直在滴水。")
        )

        CoroutineScope(Dispatchers.IO).launch {
            val count = roomDao.getAllRoomsNow().size
            if (count == 0) {
                roomDao.insertRooms(defaultRooms)
            }

            // --- 【核心修改：檢查並插入預設公告】 ---
            val announcementCount = announcementDao.getAll().firstOrNull()?.size ?: 0
            if (announcementCount == 0) {
                defaultAnnouncements.forEach { announcementDao.insert(it) }
            }

            // --- 【核心修改：檢查並插入預設報修】 ---
            val repairReportCount = repairReportDao.getAll().firstOrNull()?.size ?: 0
            if (repairReportCount == 0) {
                defaultRepairReports.forEach { repairReportDao.insert(it) }
            }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?, navController: NavHostController) {
        if (intent == null) return

        val navigateTo = intent.getStringExtra("navigateTo")
        if (navigateTo == "room_change_approval") {
            val landlordId = intent.getIntExtra("landlordId", 0)
            if (landlordId != 0) {
                navController.navigate("room_change_approval/$landlordId")
                intent.removeExtra("navigateTo")
                intent.removeExtra("landlordId")
            }
        }
    }
}