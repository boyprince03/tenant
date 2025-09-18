// RoomManageScreen.kt
package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.RoomEntity
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.navigation.NavHostController
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.app.DatePickerDialog
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomManageScreen(roomDao: RoomDao, navController: NavHostController) {
    val rooms by roomDao.getAllRooms().collectAsState(initial = emptyList())
    var editingRoom by remember { mutableStateOf<RoomEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("房間管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "選單")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("首頁") },
                            onClick = {
                                expanded = false
                                navController.navigate("mainhome")
                            }
                        )
                        // 可再加選單
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).padding(16.dp)) {
            Button(
                onClick = {
                    editingRoom = RoomEntity(roomNumber = "") // landlordCode is nullable now
                    showDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("新增房間")
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(rooms) { room ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                editingRoom = room
                                showDialog = true
                            }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("房號: ${room.roomNumber}")
                            Text("租客: ${room.tenantName}")
                            Text("房型: ${room.type}")
                            Text("租金: ${room.rentAmount}")
                            Text("押金: ${room.deposit}")
                            Text("狀態: ${if (room.status.isBlank()) "未設定" else room.status}")
                            Text("租期: ${room.rentDuration}")
                            Text("起: ${room.rentStartDate}  迄: ${room.rentEndDate}")
                            if (room.note.isNotBlank()) Text("備註: ${room.note}")
                        }
                    }
                }
            }
        }

        if (showDialog) {
            RoomEditDialog(
                room = editingRoom ?: RoomEntity(roomNumber = ""),
                onDismiss = { showDialog = false },
                onSave = { room ->
                    scope.launch {
                        roomDao.insertRoom(room)
                        showDialog = false
                    }
                },
                onDelete = { room ->
                    scope.launch {
                        roomDao.deleteRoom(room)
                        showDialog = false
                    }
                }
            )
        }
    }
}

// 編輯/新增房間 Dialog（含所有欄位）
@Composable
fun RoomEditDialog(
    room: RoomEntity,
    onDismiss: () -> Unit,
    onSave: (RoomEntity) -> Unit,
    onDelete: (RoomEntity) -> Unit
) {
    var roomNumber by remember { mutableStateOf(room.roomNumber) }
    var tenantName by remember { mutableStateOf(room.tenantName) }
    var type by remember { mutableStateOf(room.type) }
    var note by remember { mutableStateOf(room.note) }
    var rentAmount by remember { mutableStateOf(if (room.rentAmount == 0) "" else room.rentAmount.toString()) }
    var deposit by remember { mutableStateOf(if (room.deposit == 0) "" else room.deposit.toString()) }

    // 【核心修改】如果狀態為空，則預設為 "可租"
    var status by remember { mutableStateOf(room.status.ifBlank { "可租" }) }
    val statusOptions = listOf("可租", "出租中", "維修中")

    var rentEndDate by remember { mutableStateOf(room.rentEndDate) }
    var rentDuration by remember { mutableStateOf(room.rentDuration) }
    val durationOptions = listOf("0.5", "1", "2", "3")
    var rentDurationIndex by remember { mutableStateOf(1) } // 預設 1 年

    var rentStartDate by remember { mutableStateOf(room.rentStartDate) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val context = LocalContext.current
    fun showDatePicker(onDateSet: (String) -> Unit) {
        val c = Calendar.getInstance()
        if (rentStartDate.isNotBlank()) {
            try {
                c.time = sdf.parse(rentStartDate)!!
            } catch (_: Exception) {}
        }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = Calendar.getInstance()
                picked.set(y, m, d)
                onDateSet(sdf.format(picked.time))
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    LaunchedEffect(rentStartDate, rentDurationIndex) {
        if (rentStartDate.isNotBlank()) {
            try {
                val start = Calendar.getInstance()
                start.time = sdf.parse(rentStartDate)!!
                val years = when (rentDurationIndex) {
                    0 -> 0 // 0.5年
                    1 -> 1
                    2 -> 2
                    3 -> 3
                    else -> 1
                }
                val months = if (rentDurationIndex == 0) 6 else 0
                start.add(Calendar.YEAR, years)
                start.add(Calendar.MONTH, months)
                start.add(Calendar.DAY_OF_MONTH, -1)
                rentEndDate = sdf.format(start.time)
            } catch (e: Exception) {
                rentEndDate = ""
            }
        } else {
            rentEndDate = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (room.roomNumber.isBlank()) "新增房間" else "編輯房間") },
        text = {
            Column (
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = roomNumber, onValueChange = { roomNumber = it }, label = { Text("房號") })
                OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("租客姓名") })
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("房型") })

                Text("房屋狀態", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    statusOptions.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { status = option }
                        ) {
                            RadioButton(
                                selected = (status == option),
                                onClick = { status = option }
                            )
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
                    label = { Text("租金(元)") }
                )
                OutlinedTextField(
                    value = deposit,
                    onValueChange = {},
                    label = { Text("押金(2個月)") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("租期", Modifier.padding(end = 0.dp))
                    durationOptions.forEachIndexed { idx, label ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 0.dp)) {
                            RadioButton(selected = rentDurationIndex == idx, onClick = { rentDurationIndex = idx })
                            Text(label)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = rentStartDate,
                        onValueChange = {},
                        label = { Text("租賃開始日 yyyy-MM-dd") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker { picked -> rentStartDate = picked } }
                    )
                }
                OutlinedTextField(
                    value = rentEndDate,
                    onValueChange = {},
                    label = { Text("租賃結束日 yyyy-MM-dd") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("備註") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (roomNumber.isNotBlank()) {
                    onSave(
                        room.copy(
                            roomNumber = roomNumber,
                            tenantName = tenantName,
                            type = type,
                            note = note,
                            rentAmount = rentAmount.toIntOrNull() ?: 0,
                            deposit = deposit.toIntOrNull() ?: 0,
                            status = status,
                            rentStartDate = rentStartDate,
                            rentEndDate = rentEndDate,
                            rentDuration = rentDuration
                        )
                    )
                }
            }) { Text("儲存") }
        },
        dismissButton = {
            Row {
                if (room.roomNumber.isNotBlank()) {
                    Button(
                        onClick = { onDelete(room) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("刪除") }
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}