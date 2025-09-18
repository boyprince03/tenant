// 檔名: SelectRoomScreen.kt
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    userId: Int,
    db: AppDatabase,
    onNavigateBack: () -> Unit
) {
    val roomDao = db.roomDao()
    val userDao = db.userDao()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var landlordCodeInput by remember { mutableStateOf("") }
    // 【核心修改】用一個列表來儲存查詢結果，可以是多個或單個
    var landlordList by remember { mutableStateOf<List<User>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }

    // 以下三個狀態用於房間選擇對話框
    var availableRooms by remember { mutableStateOf<List<RoomEntity>>(emptyList()) }
    var showRoomSelectionDialog by remember { mutableStateOf(false) }
    var selectedLandlord by remember { mutableStateOf<User?>(null) }

    // 房間選擇對話框 (邏輯不變)
    if (showRoomSelectionDialog && selectedLandlord != null) {
        RoomSelectionDialog(
            rooms = availableRooms,
            onDismiss = { showRoomSelectionDialog = false },
            onRoomSelected = { room ->
                scope.launch {
                    val currentUser = withContext(Dispatchers.IO) { userDao.getUserById(userId) }
                    if (currentUser != null) {
                        currentUser.boundLandlordCode = room.landlordCode
                        currentUser.boundRoomNumber = room.roomNumber
                        userDao.updateUser(currentUser)

                        val updatedRoom = room.copy(
                            tenantId = userId,
                            tenantName = currentUser.username,
                            status = "出租中"
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
        Text("請輸入房東序號或留空查詢", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = landlordCodeInput,
            onValueChange = {
                landlordCodeInput = it
                errorMsg = ""
                landlordList = emptyList() // 清除上次的搜尋結果
            },
            label = { Text("房東序號 (留空可查全部)") },
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMsg.isNotBlank()) Text(errorMsg, color = MaterialTheme.colorScheme.error)

        Button(
            onClick = {
                scope.launch {
                    // 【核心修改】重寫查詢邏輯
                    if (landlordCodeInput.isNotBlank()) {
                        // 情況1: 輸入序號，精準查詢
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
                        // 情況2: 未輸入序號，查詢全部
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

        // 【核心修改】用 LazyColumn 顯示查詢結果列表
        if (landlordList.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("查詢結果", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(landlordList) { landlord ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val allRooms = withContext(Dispatchers.IO) {
                                        roomDao.getRoomsByLandlordCode(landlord.landlordCode ?: "")
                                    }
                                    val filteredRooms = allRooms.filter {
                                        it.status == "可租" && (it.tenantId == null || it.tenantId == userId)
                                    }
                                    if (filteredRooms.isEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "此房東目前無可租房間", Toast.LENGTH_SHORT).show()
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