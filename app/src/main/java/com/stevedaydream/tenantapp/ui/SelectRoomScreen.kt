// 檔名: SelectRoomScreen.kt
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
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SelectRoomScreen(
    userId: String,
    db: AppDatabase,
    onNavigateBack: () -> Unit
) {
    val roomDao = db.roomDao()
    val userDao = db.userDao()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var landlordCodeInput by remember { mutableStateOf("") }
    var landlordList by remember { mutableStateOf<List<User>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }

    var availableRooms by remember { mutableStateOf<List<RoomEntity>>(emptyList()) }
    var showRoomSelectionDialog by remember { mutableStateOf(false) }
    var selectedLandlord by remember { mutableStateOf<User?>(null) }

    // --- 【*** 核心修改：檢查使用者是否已綁定 ***】 ---
    var currentUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        currentUser = withContext(Dispatchers.IO) { userDao.getUserById(userId) }
        isLoading = false
    }
    // --- 【修改結束】 ---

    if (showRoomSelectionDialog && selectedLandlord != null) {
        RoomSelectionDialog(
            rooms = availableRooms,
            onDismiss = { showRoomSelectionDialog = false },
            onRoomSelected = { room ->
                scope.launch {
                    val userToUpdate = currentUser
                    if (userToUpdate != null) {
                        userToUpdate.boundLandlordCode = room.landlordCode
                        userToUpdate.boundRoomNumber = room.roomNumber
                        userDao.updateUser(userToUpdate)

                        val updatedRoom = room.copy(
                            tenantId = userId,
                            tenantName = userToUpdate.username,
                            status = "出租中" // 綁定後，狀態更新為出租中
                        )
                        roomDao.insertRoom(updatedRoom)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "成功綁定 ${room.roomNumber} 房！", Toast.LENGTH_LONG).show()
                            onNavigateBack()
                        }
                    }
                }
                showRoomSelectionDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 【*** 核心修改：根據綁定狀態顯示不同UI ***】 ---
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentUser?.boundRoomNumber != null) {
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
            // --- 【如果未綁定，則顯示原有的選擇介面】 ---
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
                        if (landlordCodeInput.isNotBlank()) {
                            val foundLandlord = withContext(Dispatchers.IO) {
                                userDao.getLandlordByCode(landlordCodeInput)
                            }
                            if (foundLandlord == null) {
                                errorMsg = "查無此房東序號"
                                landlordList = emptyList()
                            } else {
                                landlordList = listOf(foundLandlord)
                            }
                        } else {
                            val allLandlords = withContext(Dispatchers.IO) {
                                userDao.getAllLandlords().first()
                            }
                            if (allLandlords.isEmpty()) {
                                errorMsg = "目前沒有任何房東"
                                landlordList = emptyList()
                            } else {
                                landlordList = allLandlords
                            }
                        }
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
                                        if (landlordCode.isNullOrBlank()) {
                                            withContext(Dispatchers.Main) {
                                                Toast
                                                    .makeText(context, "此房東序號無效", Toast.LENGTH_SHORT)
                                                    .show()
                                            }
                                            return@launch
                                        }

                                        val allRooms = withContext(Dispatchers.IO) {
                                            roomDao.getRoomsByLandlordCode(landlordCode)
                                        }

                                        val filteredRooms = allRooms.filter {
                                            it.status.contains("可租", ignoreCase = true) &&
                                                    (it.tenantId == null || it.tenantId == userId)
                                        }

                                        if (filteredRooms.isEmpty()) {
                                            withContext(Dispatchers.Main) {
                                                Toast
                                                    .makeText(context, "此房東目前無可租房間", Toast.LENGTH_SHORT)
                                                    .show()
                                            }
                                        } else {
                                            selectedLandlord = landlord
                                            availableRooms = filteredRooms
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
        // --- 【修改結束】 ---
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
                items(rooms) { room ->
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