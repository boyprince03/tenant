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
        val defaultRooms = listOf("401", "402", "403", "501", "502", "503", "504").map { RoomEntity(it) }

        // 只在資料庫為空時插入，否則每次進來都插入會重複
        CoroutineScope(Dispatchers.IO).launch {
            val count = roomDao.getAllRoomsNow().size
            if (count == 0) {
                roomDao.insertRooms(defaultRooms)
            }
        }

        setContent {
            val navController = rememberNavController()
            AppNavGraph(navController, db)
        }
    }
}
