package com.stevedaydream.tenantapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.navigation.AppNavGraph
import com.stevedaydream.tenantapp.data.RoomEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val roomDao = db.roomDao()
        // 【核心修改】移除 landlordCode，讓房間預設為「無主」
        val defaultRooms = listOf(
            RoomEntity(roomNumber = "401"),
            RoomEntity(roomNumber = "402"),
            RoomEntity(roomNumber = "403"),
            RoomEntity(roomNumber = "501"),
            RoomEntity(roomNumber = "502"),
            RoomEntity(roomNumber = "503"),
            RoomEntity(roomNumber = "504")
        )

        // 只在資料庫為空時插入，否則每次進來都插入會重複
        CoroutineScope(Dispatchers.IO).launch {
            val count = roomDao.getAllRoomsNow().size
            if (count == 0) {
                roomDao.insertRooms(defaultRooms)
            }
        }

        setContent {
            val navController = rememberNavController()
            AppNavGraph(navController, db, )
        }
    }
}