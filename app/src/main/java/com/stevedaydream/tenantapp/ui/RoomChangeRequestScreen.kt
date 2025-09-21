package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
    navController: NavHostController,
    requestRepository: RoomChangeRequestRepository,
    adminRepository: AdminRepository // <-- 【*** 新增 Repository ***】
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userDao = db.userDao()
    val roomDao = db.roomDao()

    var currentUser by remember { mutableStateOf<User?>(null) }
    var availableRooms by remember { mutableStateOf<List<RoomEntity>>(emptyList()) }
    var selectedRoom by remember { mutableStateOf<RoomEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) } // <-- 【*** 新增同步狀態 ***】

    LaunchedEffect(userId) {
        isLoading = true
        userDao.getUserById(userId).collect { userFromFlow ->
            currentUser = userFromFlow
            if (userFromFlow?.boundLandlordCode != null) {
                val allRooms = roomDao.getRoomsByLandlordCode(userFromFlow.boundLandlordCode!!)
                availableRooms = allRooms.filter {
                    val userCurrentRoomNumber = userFromFlow.boundRoomNumber
                    it.status.contains("可租", ignoreCase = true) &&
                            (userCurrentRoomNumber == null || it.roomNumber != userCurrentRoomNumber)
                }
            } else {
                availableRooms = emptyList()
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("申請更換房間") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading || isSyncing) { // <-- 【*** 修改載入條件 ***】
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    if (isSyncing) {
                        Spacer(Modifier.height(8.dp))
                        Text("同步資料中...")
                    }
                }
            } else if (currentUser == null) {
                Text("無法載入使用者資訊。")
            } else if (currentUser?.boundLandlordCode == null) {
                Text("您目前未綁定任何房東，無法申請更換房間。")
            } else if (availableRooms.isEmpty()) {
                // --- 【*** 核心修改：新增同步按鈕 ***】 ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("目前沒有其他可更換的房間。")
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                val (success, message) = adminRepository.syncAllDataFromCloud()
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                isSyncing = false
                                // 同步後，LaunchedEffect 將自動重新載入資料
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新整理")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("請選擇您想更換的新房間：", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                    }

                    items(availableRooms, key = { it.id }) { room ->
                        RoomSelectionCard(
                            room = room,
                            isSelected = room.roomNumber == selectedRoom?.roomNumber,
                            onClick = { selectedRoom = room }
                        )
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
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
                                            requestedRoomNumber = room.roomNumber,
                                            status = "pending",
                                            requestDate = System.currentTimeMillis()
                                        )
                                        requestRepository.insert(newRequest)
                                        Toast.makeText(context, "請求已送出，請靜待房東審核。", Toast.LENGTH_LONG).show()
                                        navController.popBackStack()
                                    }
                                } else {
                                    Toast.makeText(context, "無法送出請求，使用者資訊不完整。", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = selectedRoom != null && currentUser?.boundRoomNumber != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("確認送出申請")
                        }
                    }
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