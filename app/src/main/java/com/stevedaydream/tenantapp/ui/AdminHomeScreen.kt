package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    navController: NavHostController,
    adminRepository: AdminRepository,
    onLogout: () -> Unit
) {
    val viewModel: AdminViewModel = viewModel(factory = AdminViewModelFactory(adminRepository))
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 【*** 新增 ***】
    // 監聽指派訊息的變化，並顯示 Toast
    LaunchedEffect(uiState.assignmentMessage) {
        uiState.assignmentMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearAssignmentMessage() // 顯示後立即清除，避免重複觸發
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理員後台") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("登出")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 【*** 新增卡片 ***】
                item {
                    AssignRoomCard(
                        uiState = uiState,
                        onLandlordSelected = viewModel::onLandlordSelected,
                        onRoomSelectionChanged = viewModel::onRoomToAssignSelectionChanged,
                        onAssignClicked = viewModel::assignSelectedRooms
                    )
                }
                item {
                    DataCard(title = "最新使用者", data = uiState.users, navController = navController) { user ->
                        Text("使用者: ${user.username} (${user.role})")
                    }
                }
                item {
                    DataCard(title = "最新房間", data = uiState.rooms, navController = navController) { room ->
                        Text("房號: ${room.roomNumber}, 狀態: ${room.status}")
                    }
                }
                item {
                    DataCard(title = "最新修繕回報", data = uiState.repairReports, navController = navController) { report ->
                        Text("回報: ${report.roomNumber} - ${report.issue}")
                    }
                }
                item {
                    DataCard(title = "最新公告", data = uiState.announcements, navController = navController) { announcement ->
                        Text("公告: ${announcement.title}")
                    }
                }
                item {
                    DataCard(title = "最新換房請求", data = uiState.roomChangeRequests, navController = navController) { request ->
                        Text("請求: ${request.tenantName} 從 ${request.currentRoomNumber} 到 ${request.requestedRoomNumber}")
                    }
                }
            }
        }
    }
}

/**
 * 【*** 新增 Composable ***】
 * 用於指派房間給房東的 UI 卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignRoomCard(
    uiState: AdminUiState,
    onLandlordSelected: (User) -> Unit,
    onRoomSelectionChanged: (String) -> Unit,
    onAssignClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("指派房間給房東", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Divider()

            // 1. 房東選擇下拉選單
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = uiState.selectedLandlord?.username ?: "請選擇房東",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("目標房東") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    uiState.landlords.forEach { landlord ->
                        DropdownMenuItem(
                            text = { Text("${landlord.username} (${landlord.landlordCode})") },
                            onClick = {
                                onLandlordSelected(landlord)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // 2. 顯示未指派的房間列表 (如果已選擇房東)
            if (uiState.selectedLandlord != null) {
                Spacer(Modifier.height(8.dp))
                Text("選擇要指派的房間:", style = MaterialTheme.typography.titleSmall)

                if (uiState.unassignedRooms.isEmpty()) {
                    Text("目前沒有未指派的房間。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // 為了避免在 LazyColumn 中使用 LazyColumn，這裡用 Column
                    // 如果房間數很多，需要考慮效能，但管理員介面通常還好
                    Column {
                        uiState.unassignedRooms.forEach { room ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRoomSelectionChanged(room.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = room.id in uiState.selectedRoomIdsToAssign,
                                    onCheckedChange = { onRoomSelectionChanged(room.id) }
                                )
                                Text("房號: ${room.roomNumber} (類型: ${room.type})")
                            }
                        }
                    }
                }
            }

            // 3. 指派按鈕
            Button(
                onClick = onAssignClicked,
                enabled = uiState.selectedLandlord != null && uiState.selectedRoomIdsToAssign.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("指派選定房間")
            }
        }
    }
}


@Composable
fun <T> DataCard(
    title: String,
    data: List<T>,
    navController: NavHostController,
    itemContent: @Composable (T) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { /* 導航到完整列表頁面 */
                    val route = when(title) {
                        "最新使用者" -> "user_list"
                        "最新房間" -> "room_list_admin"
                        "最新修繕回報" -> "repair_history_admin"
                        "最新公告" -> "announcement_admin"
                        "最新換房請求" -> "request_list_admin"
                        else -> ""
                    }
                    if (route.isNotEmpty()) navController.navigate(route)
                }) {
                    Text("更多")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (data.isEmpty()) {
                Text("沒有資料")
            } else {
                data.take(3).forEach { item -> // 只顯示前三筆
                    Box(Modifier.padding(vertical = 4.dp)) {
                        itemContent(item)
                    }
                }
            }
        }
    }
}
