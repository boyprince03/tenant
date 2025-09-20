package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomChangeRequestScreen(
    userId: String,
    db: AppDatabase,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userDao = db.userDao()
    val roomDao = db.roomDao()
    val requestDao = db.roomChangeRequestDao()

    var currentUser by remember { mutableStateOf<User?>(null) }
    var availableRooms by remember { mutableStateOf<List<RoomEntity>>(emptyList()) }
    var selectedRoom by remember { mutableStateOf<RoomEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        val user = userDao.getUserById(userId)
        currentUser = user
        if (user?.boundLandlordCode != null) {
            val allRooms = roomDao.getRoomsByLandlordCode(user.boundLandlordCode!!)
            // 過濾出可租的，且不是自己目前住的房間
            availableRooms = allRooms.filter {
                it.status.contains("可租", ignoreCase = true) && it.roomNumber != user.boundRoomNumber
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("申請更換房間") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (availableRooms.isEmpty()) {
                Text("目前沒有其他可更換的房間。")
            } else {
                Text("請選擇您想更換的新房間：", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(availableRooms) { room ->
                        RoomSelectionCard(
                            room = room,
                            isSelected = room.roomNumber == selectedRoom?.roomNumber,
                            onClick = { selectedRoom = room }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                // --- 【*** 核心修改部分 ***】 ---
                Button(
                    onClick = {
                        val user = currentUser
                        val room = selectedRoom
                        if (user != null && room != null && user.boundRoomNumber != null && user.boundLandlordCode != null) {
                            scope.launch {
                                val newRequest = RoomChangeRequest(
                                    tenantId = user.id,
                                    tenantName = user.username,
                                    landlordCode = user.boundLandlordCode!!,
                                    currentRoomNumber = user.boundRoomNumber!!,
                                    requestedRoomNumber = room.roomNumber
                                )
                                requestDao.insert(newRequest)
                                Toast.makeText(context, "請求已送出，請靜待房東審核。", Toast.LENGTH_LONG).show()

                                // 【修改點】簡化導覽邏輯，直接返回上一頁
                                // TenantHomeScreen 會自動更新狀態
                                navController.popBackStack()
                            }
                        }
                    },
                    enabled = selectedRoom != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("確認送出申請")
                }

            }
        }
    }
}

@Composable
private fun RoomSelectionCard(room: RoomEntity, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("房號: ${room.roomNumber}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text("房型: ${room.type}", style = MaterialTheme.typography.bodyMedium)
            Text("租金: ${room.rentAmount}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}