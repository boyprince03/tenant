package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.data.ElectricMeterRepository
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ElectricityQueryScreen(
    userId: String,
    db: AppDatabase,
    navController: NavHostController,
    // 【*** 核心修改 1：接收 Repository ***】
    electricMeterRepository: ElectricMeterRepository
) {
    val viewModel: ElectricityQueryViewModel = viewModel(
        // 【*** 核心修改 2：將 Repository 傳給 Factory ***】
        factory = ElectricityQueryViewModelFactory(userId, db, electricMeterRepository)
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 【*** 核心修改 3：監聽 Toast 訊息 ***】
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歷史電費查詢", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                // 【*** 核心修改 4：新增同步按鈕 ***】
                actions = {
                    IconButton(
                        onClick = { viewModel.onSyncDataClicked() },
                        enabled = !uiState.isSyncing // 同步中則禁用按鈕
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "從雲端同步資料")
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            } else {
                // 月份選擇
                MonthSelector(
                    months = uiState.availableMonths,
                    selectedMonth = uiState.selectedMonth,
                    onMonthSelected = viewModel::onMonthSelected
                )

                // 房間篩選 (僅房東可見)
                if (uiState.isLandlord) {
                    RoomFilter(
                        rooms = uiState.availableRooms.map { it.roomNumber },
                        selectedRooms = uiState.selectedRooms,
                        onRoomSelected = viewModel::onRoomSelectionChanged,
                        onSelectAll = viewModel::onSelectAllRooms
                    )
                }

                // 查詢結果
                ResultList(results = uiState.queryResults)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSelector(
    months: List<String>,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit
) {
    if (months.isEmpty()) {
        Text("無可用月份資料", color = MaterialTheme.colorScheme.error)
    } else {
        var expanded by remember { mutableStateOf(false) } // <-- 【*** 修正點 1 ***】
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedMonth,
                onValueChange = {},
                readOnly = true,
                label = { Text("選擇月份") },
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
                            onMonthSelected(month)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomFilter(
    rooms: List<String>,
    selectedRooms: Set<String>,
    onRoomSelected: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("選擇房號", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedRooms.size == rooms.size,
                onClick = { onSelectAll(selectedRooms.size != rooms.size) },
                label = { Text("全選") }
            )
            rooms.forEach { roomNo ->
                FilterChip(
                    selected = roomNo in selectedRooms,
                    onClick = { onRoomSelected(roomNo) },
                    label = { Text(roomNo) }
                )
            }
        }
    }
}

@Composable
private fun ResultList(results: List<ElectricityQueryViewModel.QueryResult>) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("此月份查無資料", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { result ->
                ResultCard(result)
            }
        }
    }
}

@Composable
private fun ResultCard(result: ElectricityQueryViewModel.QueryResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "房號: ${result.roomNumber} (${result.recordMonth})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Divider()
            // 【*** 修正點 2 (呼叫處) ***】
            QueryResultRow("本期度數", result.meterValue.toString())
            QueryResultRow("上期度數", result.previousMeterValue.toString())
            QueryResultRow("使用度數", "${result.usage} 度")
            QueryResultRow("本期電費", "${result.fee} 元")
            QueryResultRow("繳費狀態", result.paymentStatus, isStatus = true)
        }
    }
}

// 【*** 修正點 2 (定義處) ***】
@Composable
private fun QueryResultRow(label: String, value: String, isStatus: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isStatus) {
                if (value == "已繳費") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            } else {
                LocalContentColor.current
            }
        )
    }
}