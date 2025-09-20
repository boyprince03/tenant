package com.stevedaydream.tenantapp.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomManageScreen(
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
            RoomEditDialog(
                room = editingRoom!!,
                isNew = isCreatingNew,
                onDismiss = { showDialog = false },
                onSave = { room ->
                    // 【核心修改】立即關閉 Dialog，然後在背景執行儲存
                    showDialog = false
                    scope.launch {
                        try {
                            if (isCreatingNew) {
                                roomRepository.addRoom(room)
                            } else {
                                roomRepository.updateRoom(room)
                            }
                            // 成功後顯示提示
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
                    // 【核心修改】刪除也採用相同模式
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


// RoomEditDialog, RoomItemCard, InfoRow 保持不變，此處省略以保持簡潔
// 您可以繼續使用上一版本中的程式碼
@Composable
fun RoomEditDialog(
    room: RoomEntity,
    isNew: Boolean, // 用於判斷是新增還是編輯
    onDismiss: () -> Unit,
    onSave: (RoomEntity) -> Unit,
    onDelete: (RoomEntity) -> Unit
) {
    // Dialog 內部邏輯幾乎不變，只是新增一個 isNew 參數來決定是否顯示刪除按鈕
    var roomNumber by remember { mutableStateOf(room.roomNumber) }
    var tenantName by remember { mutableStateOf(room.tenantName) }
    var type by remember { mutableStateOf(room.type) }
    var note by remember { mutableStateOf(room.note) }
    var rentAmount by remember { mutableStateOf(if (room.rentAmount == 0) "" else room.rentAmount.toString()) }
    var deposit by remember { mutableStateOf(if (room.deposit == 0) "" else room.deposit.toString()) }
    var status by remember { mutableStateOf(room.status.ifBlank { "可租" }) }
    val statusOptions = listOf("可租", "出租中", "維修中")
    var rentEndDate by remember { mutableStateOf(room.rentEndDate) }
    var rentDuration by remember { mutableStateOf(room.rentDuration) }
    val durationOptions = listOf("半年", "一年", "二年")
    var rentDurationIndex by remember { mutableStateOf(durationOptions.indexOf(room.rentDuration).coerceAtLeast(1)) }
    var rentStartDate by remember { mutableStateOf(room.rentStartDate) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val context = LocalContext.current

    // DatePicker 和 LaunchedEffect 邏輯保持不變...
    fun showDatePicker(onDateSet: (String) -> Unit) {
        val c = Calendar.getInstance()
        if (rentStartDate.isNotBlank()) {
            try { c.time = sdf.parse(rentStartDate)!! } catch (_: Exception) {}
        }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = Calendar.getInstance(); picked.set(y, m, d)
                onDateSet(sdf.format(picked.time))
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    LaunchedEffect(rentStartDate, rentDurationIndex) {
        if (rentStartDate.isNotBlank()) {
            try {
                val start = Calendar.getInstance(); start.time = sdf.parse(rentStartDate)!!
                val years = when (rentDurationIndex) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 1 }
                val months = if (rentDurationIndex == 0) 6 else 0
                start.add(Calendar.YEAR, years); start.add(Calendar.MONTH, months); start.add(Calendar.DAY_OF_MONTH, -1)
                rentEndDate = sdf.format(start.time); rentDuration = durationOptions[rentDurationIndex]
            } catch (e: Exception) { rentEndDate = "" }
        } else { rentEndDate = "" }
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新增房間" else "編輯房間 - ${room.roomNumber}") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(value = roomNumber, onValueChange = { roomNumber = it }, label = { Text("房號") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("房型 (例：雅房、套房)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("租客姓名 (可留空)") }, modifier = Modifier.fillMaxWidth())
                Divider(Modifier.padding(vertical = 4.dp))
                Text("房屋狀態", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    statusOptions.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { status = option }.padding(end = 8.dp)) {
                            RadioButton(selected = (status == option), onClick = { status = option })
                            Text(text = option)
                        }
                    }
                }
                OutlinedTextField(
                    value = rentAmount,
                    onValueChange = {
                        rentAmount = it.filter { c -> c.isDigit() }
                        val amount = rentAmount.toIntOrNull() ?: 0
                        deposit = if (amount > 0) (amount * 2).toString() else ""
                    },
                    label = { Text("月租金(元)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = deposit, onValueChange = {}, label = { Text("押金(自動計算為2個月)") }, readOnly = true, enabled = false, modifier = Modifier.fillMaxWidth())
                Divider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("租期", Modifier.padding(end = 8.dp))
                    durationOptions.forEachIndexed { idx, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = rentDurationIndex == idx, onClick = { rentDurationIndex = idx })
                            Text(label)
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = rentStartDate, onValueChange = {}, label = { Text("租賃開始日") }, modifier = Modifier.fillMaxWidth(), readOnly = true)
                    Box(modifier = Modifier.matchParentSize().clickable { showDatePicker { picked -> rentStartDate = picked } })
                }
                OutlinedTextField(value = rentEndDate, onValueChange = {}, label = { Text("租賃結束日 (自動計算)") }, modifier = Modifier.fillMaxWidth(), enabled = false)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("備註") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (roomNumber.isNotBlank()) {
                    onSave(
                        room.copy(
                            roomNumber = roomNumber, tenantName = tenantName, type = type, note = note,
                            rentAmount = rentAmount.toIntOrNull() ?: 0, deposit = deposit.toIntOrNull() ?: 0,
                            status = status, rentStartDate = rentStartDate, rentEndDate = rentEndDate, rentDuration = rentDuration
                        )
                    )
                }
            }) { Text("儲存") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew) { // 只有在編輯模式下才顯示刪除按鈕
                    Button(
                        onClick = { onDelete(room) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("刪除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

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

