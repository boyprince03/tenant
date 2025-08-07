package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.ElectricMeterDao
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ElectricityQueryScreen(
    roomDao: RoomDao,
    electricMeterDao: ElectricMeterDao,
    navController: NavHostController
) {
    val allRooms by roomDao.getAllRooms().collectAsState(initial = emptyList())
    val allRecords by electricMeterDao.getAllRecords().collectAsState(initial = emptyList())

    val roomNumbers = allRooms.map { it.roomNumber }
    val months = allRecords.map { it.recordMonth }.distinct().sortedDescending()

    var selectedRooms by remember { mutableStateOf(roomNumbers.toSet()) }
    var selectedMonth by remember { mutableStateOf(months.firstOrNull() ?: "") }
    var calculatedFees by remember { mutableStateOf(mapOf<String, Int>()) }
    var showFeeInfoDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 費用計算邏輯 (假設每度電費為 5 元)
    val calculateFees: () -> Unit = {
        val fees = mutableMapOf<String, Int>()
        scope.launch {
            allRecords
                .filter { it.recordMonth == selectedMonth }
                .groupBy { it.roomNumber }
                .forEach { (roomNo, records) ->
                    val currentRecord = records.firstOrNull()
                    if (currentRecord != null) {
                        val lastTwoRecords = electricMeterDao.getLastTwoRecords(roomNo)
                        val previousMonthRecord = lastTwoRecords.find { it.recordMonth != selectedMonth }
                        if (previousMonthRecord != null) {
                            val usage = currentRecord.meterValue - previousMonthRecord.meterValue
                            if (usage > 0) {
                                fees[roomNo] = usage * 5 // 每度電費為 5 元
                            } else {
                                fees[roomNo] = 0
                            }
                        }
                    }
                }
            calculatedFees = fees
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("電費查詢", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showFeeInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "電費資訊")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 月份選擇
            Text("選擇月份", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (months.isEmpty()) {
                Text("無可用月份資料", color = MaterialTheme.colorScheme.error)
            } else {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("月份") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        months.forEach { month ->
                            DropdownMenuItem(
                                text = { Text(month) },
                                onClick = {
                                    selectedMonth = month
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 房間篩選 (FlowRow 讓卡片自動換行)
            Text("選擇房號", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                roomNumbers.forEach { roomNo ->
                    FilterChip(
                        selected = roomNo in selectedRooms,
                        onClick = {
                            selectedRooms = if (roomNo in selectedRooms) {
                                selectedRooms - roomNo
                            } else {
                                selectedRooms + roomNo
                            }
                        },
                        label = { Text(roomNo) }
                    )
                }
            }

            // 查詢按鈕
            ElevatedButton(
                onClick = { calculateFees() },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMonth.isNotBlank() && selectedRooms.isNotEmpty()
            ) {
                Icon(Icons.Default.Calculate, contentDescription = "計算電費", modifier = Modifier.padding(end = 8.dp))
                Text("計算本月電費")
            }

            // 顯示電費結果
            if (calculatedFees.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("電費明細：", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(calculatedFees.entries.toList().sortedBy { it.key }) { (roomNo, fee) ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("房號: $roomNo", style = MaterialTheme.typography.bodyLarge)
                                    Text("電費: $fee 元", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 顯示原始紀錄
            Text("本月度數紀錄", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Divider(Modifier.padding(vertical = 4.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val showRecords = allRecords
                    .filter { it.roomNumber in selectedRooms && it.recordMonth == selectedMonth }
                    .sortedWith(compareBy({ it.roomNumber }, { it.recordMonth }))

                if (showRecords.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("本月查無度數資料", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(showRecords) { record ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("房號: ${record.roomNumber}", style = MaterialTheme.typography.bodyLarge)
                                Text("度數: ${record.meterValue}", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFeeInfoDialog) {
        AlertDialog(
            onDismissRequest = { showFeeInfoDialog = false },
            title = { Text("電費計算說明") },
            text = {
                Column {
                    Text("電費計算方式如下：")
                    Spacer(Modifier.height(8.dp))
                    Text("本月電費 = (本月度數 - 上月度數) * 每度電費")
                    Text("目前設定的每度電費為：\$5 元", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = { showFeeInfoDialog = false }) {
                    Text("了解")
                }
            }
        )
    }
}
