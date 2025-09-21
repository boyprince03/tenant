@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.Announcement
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.User

@Composable
fun TenantHomeScreen(
    navController: NavHostController,
    viewModelFactory: TenantViewModelFactory,
    onLogout: () -> Unit
) {
    val viewModel: TenantViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    var showRoomInfoDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf<Announcement?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("租客系統", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                actions = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "選單")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("回報紀錄") },
                            onClick = {
                                expanded = false
                                uiState.currentUser?.id?.let { navController.navigate("history/$it") }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("登出") },
                            leadingIcon = { Icon(Icons.Default.Logout, contentDescription = "登出")},
                            onClick = {
                                expanded = false
                                onLogout()
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
        } else {
            val currentUser = uiState.currentUser
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "歡迎！ ${currentUser?.username ?: ""}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    uiState.latestRequest?.let { request ->
                        val (statusText, bgColor) = when (request.status) {
                            "pending" -> "換房審核中" to MaterialTheme.colorScheme.secondary
                            "approved" -> "換房已核准" to MaterialTheme.colorScheme.primary
                            "rejected" -> "換房被拒絕" to MaterialTheme.colorScheme.error
                            else -> "" to Color.Transparent
                        }

                        if (statusText.isNotEmpty()) {
                            Text(
                                text = statusText,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .background(bgColor, RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    "📢 最新公告",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.announcements.isEmpty()) {
                            Text("目前沒有公告", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            uiState.announcements.take(3).forEachIndexed { index, ann ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDetailDialog = ann }
                                ) {
                                    Text(ann.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(ann.content, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (index < uiState.announcements.take(3).size - 1) {
                                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                        TextButton(
                            onClick = { currentUser?.id?.let { navController.navigate("announcement/$it") } },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp)
                        ) { Text("查看更多公告") }
                    }
                }

                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showRoomInfoDialog = true },
                    enabled = currentUser?.boundRoomNumber != null
                ) {
                    Icon(Icons.Default.Info, contentDescription = "租屋資訊", modifier = Modifier.padding(end = 8.dp))
                    Text("查看我的租屋資訊", style = MaterialTheme.typography.bodyLarge)
                }

                // --- 【*** 以下為新增/修正的按鈕 ***】 ---
                // 條件：必須綁定房間，且沒有正在審核中的請求
                if (currentUser?.boundRoomNumber != null && uiState.latestRequest?.status != "pending") {
                    ElevatedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { currentUser.id.let { navController.navigate("request_room_change/$it") } }
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "更換房間", modifier = Modifier.padding(end = 8.dp))
                        Text("申請更換房間", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                // --- 【*** 新增結束 ***】 ---
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { currentUser?.id?.let { navController.navigate("tenant_payment/$it") } }
                ) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = "繳費查詢", modifier = Modifier.padding(end = 8.dp))
                    Text("當月繳費查詢", style = MaterialTheme.typography.bodyLarge)
                }
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { currentUser?.id?.let { navController.navigate("select_room/$it") } },
                    enabled = currentUser?.boundRoomNumber == null
                ) {
                    Icon(Icons.Default.HomeWork, contentDescription = "綁定房間", modifier = Modifier.padding(end = 8.dp))
                    Text(
                        if (currentUser?.boundRoomNumber != null) "已綁定房間" else "綁定房東及房間",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { currentUser?.id?.let { navController.navigate("home/$it") } }
                ) {
                    Icon(Icons.Default.Engineering, contentDescription = "修繕回報", modifier = Modifier.padding(end = 8.dp))
                    Text("前往填寫修繕回報", style = MaterialTheme.typography.bodyLarge)
                }
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { currentUser?.id?.let { navController.navigate("electricity_query/$it") } }
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = "電費查詢", modifier = Modifier.padding(end = 8.dp))
                    Text("歷史電費查詢", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (showRoomInfoDialog) {
            RoomInfoDialog(
                room = uiState.roomDetails,
                landlord = uiState.landlord,
                tenant = uiState.currentUser,
                paymentStatus = uiState.paymentStatus,
                onDismiss = { showRoomInfoDialog = false }
            )
        }

        if (showDetailDialog != null) {
            AlertDialog(
                onDismissRequest = { showDetailDialog = null },
                title = { Text(showDetailDialog!!.title, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(showDetailDialog!!.content, style = MaterialTheme.typography.bodyLarge)
                    }
                },
                confirmButton = {
                    Button(onClick = { showDetailDialog = null }) { Text("關閉") }
                }
            )
        }
    }
}

/**
 * 【*** 新增 ***】
 * 顯示房間、房東和租客資訊的對話框 Composable。
 */
@Composable
private fun RoomInfoDialog(
    room: RoomEntity?,
    landlord: User?,
    tenant: User?,
    paymentStatus: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的租屋資訊", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(label = "目前房號", value = room?.roomNumber ?: "未綁定")
                InfoRow(label = "房東姓名", value = landlord?.username ?: "N/A")
                InfoRow(label = "房東電話", value = landlord?.phone ?: "N/A")
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                InfoRow(label = "租客姓名", value = tenant?.username ?: "N/A")
                InfoRow(label = "月租金", value = "${room?.rentAmount ?: 0} 元")
                InfoRow(label = "押金", value = "${room?.deposit ?: 0} 元")
                InfoRow(label = "起租日", value = room?.rentStartDate?.ifBlank { "N/A" } ?: "N/A")
                InfoRow(label = "到期日", value = room?.rentEndDate?.ifBlank { "N/A" } ?: "N/A")
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                InfoRow(label = "當月繳費狀態", value = paymentStatus)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

/**
 * 【*** 新增 ***】
 * 用於在對話框中顯示標籤和值的輔助 Composable。
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(120.dp) // 統一標籤寬度以便對齊
        )
        Text(text = value)
    }
}
