package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.RoomRepository
import com.stevedaydream.tenantapp.data.User
import com.stevedaydream.tenantapp.ui.shared.RoomEditDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandlordRoomManageScreen(
    roomRepository: RoomRepository,
    currentUser: User?,
    navController: NavHostController
) {
    val landlordCode = currentUser?.landlordCode ?: ""
    val rooms by roomRepository.getRoomsForLandlord(landlordCode).collectAsState(initial = emptyList())

    var editingRoom by remember { mutableStateOf<RoomEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("房間資料管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingRoom = RoomEntity(landlordCode = landlordCode)
                    isCreatingNew = true
                    showDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增房間")
            }
        }
    ) { innerPadding ->

        if (rooms.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "您尚未建立任何房間資料",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rooms, key = { it.id }) { room ->
                    RoomItemCard(room = room) {
                        editingRoom = room
                        isCreatingNew = false
                        showDialog = true
                    }
                }
            }
        }

        if (showDialog && editingRoom != null) {
            // 【*** 核心修正：使用共用的 Dialog ***】
            // 房東只能管理自己的房間，所以傳入空的房東列表
            RoomEditDialog(
                room = editingRoom!!,
                isNew = isCreatingNew,
                allLandlords = emptyList(), // 房東介面不顯示指派選單
                onDismiss = { showDialog = false },
                onSave = { room,imageUris ->
                    showDialog = false
                    scope.launch {
                        try {
                            if (isCreatingNew) {
                                roomRepository.addRoom(room)
                            } else {
                                roomRepository.updateRoom(room)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "儲存成功！", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "儲存失敗: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onDelete = { room ->
                    showDialog = false
                    scope.launch {
                        try {
                            roomRepository.deleteRoom(room)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "刪除成功！", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "刪除失敗: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }
    }
}

// 其餘 Composable (RoomItemCard, InfoRow) 保持不變
@Composable
fun RoomItemCard(room: RoomEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("房號: ${room.roomNumber}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "狀態: ${room.status.ifBlank { "未設定" }}", style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        room.status.contains("可租") -> MaterialTheme.colorScheme.primary
                        room.status.contains("出租中") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            InfoRow("租客", room.tenantName.ifBlank { "無" })
            InfoRow("房型", room.type.ifBlank { "未設定" })
            InfoRow("租金", if (room.rentAmount > 0) "${room.rentAmount} 元" else "未設定")
            if (room.rentStartDate.isNotBlank()) {
                InfoRow("租期", "${room.rentStartDate} ~ ${room.rentEndDate}")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(text = "$label:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}