// ElectricityCalcScreen.kt
package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.ElectricMeterDao
import com.stevedaydream.tenantapp.data.ElectricMeterRecord
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityCalcScreen(
    roomDao: RoomDao,
    meterDao: ElectricMeterDao,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val roomList by roomDao.getAllRooms().collectAsState(initial = emptyList())

    val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    var currentMonth by remember { mutableStateOf(LocalDate.now().format(monthFormatter)) }
    var meterMap by remember { mutableStateOf<Map<String, String>>(mapOf()) }
    var usedMap by remember { mutableStateOf<Map<String, Int>>(mapOf()) }
    var message by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // 讀取本月已有的度數（僅做範例，實務可自動載入）
    LaunchedEffect(currentMonth, roomList) {
        meterMap = roomList.associate { room ->
            room.roomNumber to ""
        }
        usedMap = emptyMap()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("電表計算") },
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
        ) }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).padding(16.dp)) {
            OutlinedTextField(
                value = currentMonth,
                onValueChange = { currentMonth = it },
                label = { Text("本月月份 (yyyy-MM)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text("請輸入各房間本月電表度數：", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(roomList) { room ->
                    OutlinedTextField(
                        value = meterMap[room.roomNumber] ?: "",
                        onValueChange = { value ->
                            meterMap = meterMap.toMutableMap().apply { put(room.roomNumber, value) }
                        },
                        label = { Text("${room.roomNumber} 房") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        var hasError = false
                        roomList.forEach { room ->
                            val v = meterMap[room.roomNumber]?.toIntOrNull()
                            if (v != null) {
                                meterDao.insertOrUpdate(
                                    ElectricMeterRecord(
                                        roomNumber = room.roomNumber,
                                        recordMonth = currentMonth,
                                        meterValue = v
                                    )
                                )
                            } else {
                                hasError = true
                            }
                        }
                        message = if (hasError) "部分房間輸入無效，未儲存" else "度數已儲存"
                        // 計算本月用電
                        val used = mutableMapOf<String, Int>()
                        roomList.forEach { room ->
                            val lastTwo = meterDao.getLastTwoRecords(room.roomNumber)
                            if (lastTwo.size == 2) {
                                used[room.roomNumber] = lastTwo[0].meterValue - lastTwo[1].meterValue
                            }
                        }
                        usedMap = used
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("儲存/計算") }
            if (message.isNotEmpty()) {
                Text(message, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            if (usedMap.isNotEmpty()) {
                Text("各房間本月用電：", style = MaterialTheme.typography.titleMedium)
                usedMap.forEach { (roomNo, used) ->
                    Text("$roomNo 房：$used 度")
                }
            }
        }
    }
}
