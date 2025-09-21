package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                data.forEach { item ->
                    Box(Modifier.padding(vertical = 4.dp)) {
                        itemContent(item)
                    }
                }
            }
        }
    }
}