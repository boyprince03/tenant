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

    // 從資料庫獲取所有房東列表
    val landlords by userDao.getAllLandlords().collectAsState(initial = emptyList())
    // 儲存選定房東的可租房間
    var availableRooms by remember { mutableStateOf<List<RoomEntity>>(emptyList()) }
    // 控制房間選擇對話框的顯示
    var showRoomSelectionDialog by remember { mutableStateOf(false) }
    // 儲存目前點擊的房東
    var selectedLandlord by remember { mutableStateOf<User?>(null) }

    // 當 showRoomSelectionDialog 和 selectedLandlord 狀態改變時觸發
    if (showRoomSelectionDialog && selectedLandlord != null) {
        RoomSelectionDialog(
            rooms = availableRooms,
            onDismiss = { showRoomSelectionDialog = false },
            onRoomSelected = { room ->
                scope.launch {
                    val currentUser = withContext(Dispatchers.IO) {
                        userDao.getUserById(userId)
                    }
                    if (currentUser != null) {
                        // 1. 更新使用者資料
                        currentUser.boundLandlordCode = room.landlordCode
                        currentUser.boundRoomNumber = room.roomNumber
                        userDao.updateUser(currentUser)

                        // 2. 更新房間資料
                        val updatedRoom = room.copy(
                            tenantId = userId,
                            tenantName = currentUser.username
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("請選擇房東以查看可租房間", style = MaterialTheme.typography.headlineSmall)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(landlords) { landlord ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                // 查找該房東所有尚未被綁定的房間
                                val allRooms = withContext(Dispatchers.IO) {
                                    roomDao.getRoomsByLandlordCode(landlord.landlordCode ?: "")
                                }
                                val filteredRooms = allRooms.filter { it.tenantId == null || it.tenantId == userId }

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