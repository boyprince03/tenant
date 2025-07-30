import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.RoomEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SelectRoomScreen(
    roomDao: RoomDao,
    onRoomSelected: (RoomEntity) -> Unit,
    onNavigateBack: () -> Unit
) {
    var landlordCodeInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var rooms by remember { mutableStateOf<List<RoomEntity>>(emptyList()) }
    var showRoomList by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text("請輸入房東序號", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = landlordCodeInput,
            onValueChange = { landlordCodeInput = it; errorMsg = ""; showRoomList = false },
            label = { Text("房東序號") },
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMsg.isNotBlank()) Text(errorMsg, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                scope.launch {
                    val foundRooms = withContext(Dispatchers.IO) {
                        roomDao.getRoomsByLandlordCode(landlordCodeInput)
                    }
                    if (foundRooms.isEmpty()) {
                        errorMsg = "查無此房東序號"
                        showRoomList = false
                    } else {
                        rooms = foundRooms
                        showRoomList = true
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) { Text("查詢房東房間") }

        Spacer(Modifier.height(24.dp))

        if (showRoomList) {
            Text("請選擇房間", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(rooms) { room ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onRoomSelected(room) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("房號: ${room.roomNumber}")
                            // 你可以加上房型/狀態等欄位
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}
