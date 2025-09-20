package com.stevedaydream.tenantapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.navigation.AppNavGraph
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.ui.theme.TenantAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val roomDao = db.roomDao()

        // 【核心修改】建立房間時，直接設定狀態為 "可租"
        val defaultRooms = listOf(
            RoomEntity(roomNumber = "401", status = "可租"),
            RoomEntity(roomNumber = "402", status = "可租"),
            RoomEntity(roomNumber = "403", status = "可租"),
            RoomEntity(roomNumber = "501", status = "可租"),
            RoomEntity(roomNumber = "502", status = "可租"),
            RoomEntity(roomNumber = "503", status = "可租"),
            RoomEntity(roomNumber = "504", status = "可租")
        )

        CoroutineScope(Dispatchers.IO).launch {
            val count = roomDao.getAllRoomsNow().size
            if (count == 0) {
                roomDao.insertRooms(defaultRooms)
            }
        }

        setContent {
            TenantAppTheme {
                val navController = rememberNavController()
                AppNavGraph(navController, db)
            }
        }
    }
}