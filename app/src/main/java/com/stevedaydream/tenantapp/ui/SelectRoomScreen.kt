import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SelectRoomScreen(
    userId: String,
    // 【核心修改】注入 Repositories
    userRepository: UserRepository,
    roomRepository: RoomRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var landlordCodeInput by remember { mutableStateOf("") }
    var landlordList by remember { mutableStateOf<List<User>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }

    var availableRooms by remember { mutableStateOf<List<RoomEntity>>(emptyList()) }
    var showRoomSelectionDialog by remember { mutableStateOf(false) }

    var currentUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        // 使用 Repository 獲取使用者
        currentUser = userRepository.getUser(userId)
        isLoading = false
    }

    if (showRoomSelectionDialog) {
        RoomSelectionDialog(
            rooms = availableRooms,
            onDismiss = { showRoomSelectionDialog = false },
            onRoomSelected = { room ->
                showRoomSelectionDialog = false // 立即關閉 Dialog
                scope.launch {
                    val userToUpdate = currentUser
                    if (userToUpdate != null) {
                        try {
                            // 1. 更新使用者資料
                            val updatedUser = userToUpdate.copy(
                                boundLandlordCode = room.landlordCode,
                                boundRoomNumber = room.roomNumber
                            )
                            userRepository.updateUser(updatedUser)

                            // 2. 更新房間資料，並將狀態改為 "出租中"
                            val updatedRoom = room.copy(
                                tenantId = userToUpdate.id,
                                tenantName = userToUpdate.username,
                                status = "出租中" // <-- 核心邏輯
                            )
                            roomRepository.updateRoom(updatedRoom)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "成功綁定 ${room.roomNumber} 房！", Toast.LENGTH_LONG).show()
                                onNavigateBack()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "綁定失敗: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentUser?.boundRoomNumber != null) {
            // 已綁定房間的 UI (保持不變)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("您已綁定房間", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "房號: ${currentUser?.boundRoomNumber}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text("返回")
                }
            }
        } else {
            // 未綁定房間的 UI
            Text("請輸入房東序號或留空查詢", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = landlordCodeInput,
                onValueChange = {
                    landlordCodeInput = it
                    errorMsg = ""
                    landlordList = emptyList()
                },
                label = { Text("房東序號 (留空可查全部)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (errorMsg.isNotBlank()) Text(errorMsg, color = MaterialTheme.colorScheme.error)

            Button(
                onClick = {
                    scope.launch {
                        // 【核心修改】使用 Repository 查詢房東
                        val foundLandlords = if (landlordCodeInput.isNotBlank()) {
                            userRepository.getLandlordByCode(landlordCodeInput)?.let { listOf(it) } ?: emptyList()
                        } else {
                            userRepository.getAllLandlords()
                        }

                        if (foundLandlords.isEmpty()) {
                            errorMsg = "查無房東資料"
                        }
                        landlordList = foundLandlords
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("查詢房東") }

            if (landlordList.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("查詢結果 (請點選要綁定的房東)", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(landlordList) { landlord ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val landlordCode = landlord.landlordCode
                                        if (landlordCode.isNullOrBlank()) return@launch

                                        // 【核心修改】使用 Repository 查詢可租房間
                                        val rooms = roomRepository.getAvailableRoomsForLandlord(landlordCode)
                                        if (rooms.isEmpty()) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "此房東目前無空房", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            availableRooms = rooms
                                            showRoomSelectionDialog = true
                                        }
                                    }
                                }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("房東: ${landlord.username}", style = MaterialTheme.typography.titleMedium)
                                Text("電話: ${landlord.phone}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun RoomSelectionDialog(
    rooms: List<RoomEntity>,
    onDismiss: () -> Unit,
    onRoomSelected: (RoomEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("請選擇要綁定的房間") },
        text = {
            LazyColumn {
                items(rooms, key = { it.id }) { room -> // 使用 id 作為 key
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onRoomSelected(room) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("房號: ${room.roomNumber}", style = MaterialTheme.typography.bodyLarge)
                            Text("房型: ${room.type}", style = MaterialTheme.typography.bodyMedium)
                            Text("租金: ${room.rentAmount}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
