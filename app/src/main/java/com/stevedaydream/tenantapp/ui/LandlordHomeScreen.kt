@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LandlordHomeScreen(
    navController: NavHostController,
    viewModelFactory: LandlordViewModelFactory,
    onLogout: () -> Unit
) {
    val viewModel: LandlordViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN)

    var expanded by remember { mutableStateOf(false) }
    var codeVisible by remember { mutableStateOf(false) }
    // var showResetConfirmDialog by remember { mutableStateOf(false) } // <-- 已移除

    fun maskCode(code: String): String {
        return if (code.length <= 4) "*".repeat(code.length)
        else code.take(2) + "*".repeat(code.length - 4) + code.takeLast(2)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("房東後台", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
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
                                uiState.landlord?.id?.let { navController.navigate("history/$it") }
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
        } else if (uiState.landlord == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("找不到房東資料，可能是新帳號。請嘗試重新登入。")
            }
        } else {
            val landlord = uiState.landlord!!
            val landlordCode = landlord.landlordCode ?: "無"
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("您的房東序號: ", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (codeVisible) landlordCode else maskCode(landlordCode),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row {
                        IconButton(onClick = { codeVisible = !codeVisible }) {
                            Icon(
                                imageVector = if (codeVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (codeVisible) "隱藏序號" else "顯示序號"
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(landlordCode))
                                Toast.makeText(context, "序號已複製！", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileCopy,
                                contentDescription = "複製序號"
                            )
                        }
                    }
                }
                if (uiState.pendingChangeRequests.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("room_change_approval/${landlord.id}") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("房間更換請求", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("您有 ${uiState.pendingChangeRequests.size} 則新的請求待審核", style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                    Column(Modifier.padding(16.dp)) {
                        if (uiState.announcements.isEmpty()) {
                            Text("目前沒有公告", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            uiState.announcements.take(3).forEach {
                                Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(it.content, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        TextButton(
                            onClick = { navController.navigate("announcement/${landlord.id}") },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp)
                        ) { Text("查看更多公告") }
                    }
                }

                Text(
                    "🛠️ 最新修繕回報",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (uiState.repairReports.isEmpty()) {
                            Text("目前沒有新的修繕回報", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            uiState.repairReports.take(3).forEach { report ->
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ){
                                        Text(
                                            "房號: ${report.roomNumber} - ${report.issue}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val statusColor = when (report.status) {
                                            "已完成" -> Color.Gray
                                            "處理中" -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                        Text(
                                            text = report.status,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                    Text(
                                        "租客: ${report.tenantName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "描述: ${report.description}",
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "回報時間: ${dateFormat.format(Date(report.date))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Divider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { navController.navigate("history/${landlord.id}") },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp)
                        ) { Text("查看所有回報") }

                    }
                }

                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate("room_manage/${landlord.id}") }
                ) {
                    Icon(Icons.Default.Home, contentDescription = "房間資料管理", modifier = Modifier.padding(end = 8.dp))
                    Text("房間資料管理", style = MaterialTheme.typography.bodyLarge)
                }
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate("contract") }
                ) {
                    Icon(Icons.Default.Description, contentDescription = "產生電子合約", modifier = Modifier.padding(end = 8.dp))
                    Text("產生電子合約（PDF）", style = MaterialTheme.typography.bodyLarge)
                }
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate("electricity/landlord") }
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = "電表計算頁面", modifier = Modifier.padding(end = 8.dp))
                    Text("電表計算頁面", style = MaterialTheme.typography.bodyLarge)
                }
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate("electricity_query/${landlord.id}") }
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = "歷史電費查詢", modifier = Modifier.padding(end = 8.dp))
                    Text("歷史電費查詢", style = MaterialTheme.typography.bodyLarge)
                }
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate("excel_import") }
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "匯入 Excel 資料", modifier = Modifier.padding(end = 8.dp))
                    Text("匯入 Excel 資料", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
