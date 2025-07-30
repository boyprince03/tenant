package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.google.accompanist.flowlayout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.ElectricMeterDao
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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

    // 狀態
    var selectedRooms by remember { mutableStateOf(roomNumbers.toSet()) }
    var selectedMonth by remember { mutableStateOf(months.firstOrNull() ?: "") }
    var menuExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }

    // 計算前月月份字串
    fun findPrevMonth(month: String): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val date = try { sdf.parse(month) } catch (_: Exception) { null }
        if (date == null) return ""
        val cal = Calendar.getInstance().apply { time = date }
        cal.add(Calendar.MONTH, -1)
        return sdf.format(cal.time)
    }
    val prevMonth = if (selectedMonth.isNotEmpty()) findPrevMonth(selectedMonth) else ""

    // 計算各房間用電量
    val usedMap = remember(selectedRooms, selectedMonth, allRecords) {
        mutableMapOf<String, Int>().apply {
            selectedRooms.forEach { roomNo ->
                val thisMonth = allRecords.find { it.roomNumber == roomNo && it.recordMonth == selectedMonth }?.meterValue
                val lastMonth = allRecords.find { it.roomNumber == roomNo && it.recordMonth == prevMonth }?.meterValue
                if (thisMonth != null && lastMonth != null) {
                    this[roomNo] = thisMonth - lastMonth
                }
            }
        }
    }
    val totalUsed = usedMap.values.sum()

    // 分段台電電費（110V住家）
    fun calcTaiPowerBill(totalKwh: Int): Int {
        var fee = 0.0
        val steps = listOf(
            Pair(120, 2.10),
            Pair(330, 3.02),
            Pair(Int.MAX_VALUE, 4.41)
        )
        var remain = totalKwh
        var last = 0
        for ((limit, price) in steps) {
            val stepKwh = if (limit == Int.MAX_VALUE) remain else (limit - last)
            val use = minOf(remain, stepKwh)
            fee += use * price
            remain -= use
            last = limit
            if (remain <= 0) break
        }
        return fee.toInt()
    }
    val totalBill = calcTaiPowerBill(totalUsed)
    val roomFeeMap = usedMap.mapValues { (room, used) ->
        if (totalUsed > 0) (totalBill * used / totalUsed.toDouble()).toInt() else 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("度數查詢") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "選單")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("首頁") },
                            onClick = {
                                menuExpanded = false
                                navController.navigate("mainhome")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("返回") },
                            onClick = {
                                menuExpanded = false
                                navController.popBackStack()
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).padding(16.dp)) {
            // 房間多選篩選器
            Text("選擇房間：", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                mainAxisSpacing = 8.dp,
                crossAxisSpacing = 8.dp
            ) {
                roomNumbers.forEach { roomNo ->
                    FilterChip(
                        selected = selectedRooms.contains(roomNo),
                        onClick = {
                            selectedRooms = if (selectedRooms.contains(roomNo))
                                selectedRooms - roomNo else selectedRooms + roomNo
                        },
                        label = { Text(roomNo) }
                    )
                }
                if (selectedRooms.size < roomNumbers.size) {
                    OutlinedButton(
                        onClick = { selectedRooms = roomNumbers.toSet() },
                        content = { Text("全選") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 月份篩選
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("月份：", Modifier.padding(end = 8.dp))
                ExposedDropdownMenuBox(
                    expanded = monthExpanded,
                    onExpandedChange = { monthExpanded = !monthExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().width(120.dp),
                        label = { Text("月份") }
                    )
                    ExposedDropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = { monthExpanded = false }
                    ) {
                        months.forEach { month ->
                            DropdownMenuItem(
                                text = { Text(month) },
                                onClick = {
                                    selectedMonth = month
                                    monthExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 電費與用電結果
            if (selectedMonth.isNotBlank() && usedMap.isNotEmpty()) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("台電分段電價計算", style = MaterialTheme.typography.titleMedium)
                        Text("本月選取房間總用電：$totalUsed 度")
                        Text("總電費：約 $totalBill 元", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        usedMap.forEach { (room, used) ->
                            val fee = roomFeeMap[room] ?: 0
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$room 房：$used 度")
                                Text("電費：約 $fee 元")
                            }
                        }
                    }
                }
            }

            // 原有紀錄列表
            LazyColumn {
                val showRecords = allRecords
                    .filter { it.roomNumber in selectedRooms && it.recordMonth == selectedMonth }
                    .sortedWith(compareBy({ it.roomNumber }, { it.recordMonth }))

                if (showRecords.isEmpty()) {
                    item { Text("本月查無度數資料", color = MaterialTheme.colorScheme.error) }
                } else {
                    items(showRecords) { record ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("房號: ${record.roomNumber}")
                                Text("月份: ${record.recordMonth}")
                                Text("度數: ${record.meterValue}")
                            }
                        }
                    }
                }
            }
        }
    }
}
