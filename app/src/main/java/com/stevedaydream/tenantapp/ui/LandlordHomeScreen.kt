@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.DeleteForever
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.data.User
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import com.stevedaydream.tenantapp.data.AdminRepository
import com.stevedaydream.tenantapp.data.RoomChangeRequestRepository
import kotlinx.coroutines.launch

@Composable
fun LandlordHomeScreen(
    landlord: User,
    // 【*** 核心修改 1：接收 Repository ***】
    requestRepository: RoomChangeRequestRepository,
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val announcementDao = db.announcementDao()
    val repairReportDao = db.repairReportDao()
    val announcements by announcementDao.getAll().collectAsState(initial = emptyList())
    val repairReports by repairReportDao.getAll().collectAsState(initial = emptyList())

    // 【*** 核心修改 2：從 Repository 監聽資料 ***】
    val changeRequests by requestRepository.getRequestsByLandlord(landlord.landlordCode ?: "")
        .collectAsState(initial = emptyList())
    val pendingRequests = changeRequests.filter { it.status == "pending" }

    val landlordCode = landlord.landlordCode ?: "無"

    var expanded by remember { mutableStateOf(false) }
    var codeVisible by remember { mutableStateOf(false) }
    fun maskCode(code: String): String {
        return if (code.length <= 4) "*".repeat(code.length)
        else code.take(2) + "*".repeat(code.length - 4) + code.takeLast(2)
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN)
// --- 【核心修改 1：加入重置功能的狀態和邏輯】 ---
    val scope = rememberCoroutineScope()
    val adminRepository = remember { AdminRepository() }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }

    // 重置確認對話框
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("確認重置？", fontWeight = FontWeight.Bold) },
            text = { Text("此操作將會刪除 Firestore 雲端資料庫中的所有資料，且無法復原。確定要繼續嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        isResetting = true
                        scope.launch {
                            val success = adminRepository.resetEntireDatabase()
                            isResetting = false
                            if (success) {
                                Toast.makeText(context, "Firestore 資料庫已重置！請重新啟動 App。", Toast.LENGTH_LONG).show()
                                // 重置成功後最好直接登出或關閉 App
                                onLogout()
                            } else {
                                Toast.makeText(context, "重置失敗，請檢查 Logcat。", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("確定刪除") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    // 正在重置的遮罩
    if (isResetting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("正在重置資料庫...", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
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
                                onNavigate("history")
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
                        // --- 【核心修改 2：在選單中加入開發者選項】 ---
                        Divider()
                        DropdownMenuItem(
                            text = { Text("重置雲端資料庫 (開發用)", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = "重置", tint = MaterialTheme.colorScheme.error)},
                            onClick = {
                                expanded = false
                                showResetConfirmDialog = true // 顯示確認對話框
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ... (房東序號顯示區)
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
            // 房間更換請求通知卡片
            if (pendingRequests.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate("room_change_approval/${landlord.id}") },
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
                            Text("您有 ${pendingRequests.size} 則新的請求待審核", style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 最新公告卡片
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
                    if (announcements.isEmpty()) {
                        Text("目前沒有公告", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        announcements.take(3).forEach {
                            Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(it.content, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    TextButton(
                        onClick = { onNavigate("announcement") },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp)
                    ) { Text("查看更多公告") }
                }
            }

            // 優化後的修繕回報區塊
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
                    if (repairReports.isEmpty()) {
                        Text("目前沒有新的修繕回報", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        repairReports.take(3).forEach { report ->
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
                        onClick = { onNavigate("history") },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp)
                    ) { Text("查看所有回報") }

                }
            }

            // 功能按鈕
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("history") }
            ) {
                Icon(Icons.Default.Engineering, contentDescription = "查詢修繕回報", modifier = Modifier.padding(end = 8.dp))
                Text("查詢修繕回報資料", style = MaterialTheme.typography.bodyLarge)
            }
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("contract") }
            ) {
                Icon(Icons.Default.Description, contentDescription = "產生電子合約", modifier = Modifier.padding(end = 8.dp))
                Text("產生電子合約（PDF）", style = MaterialTheme.typography.bodyLarge)
            }
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("room_manage") }
            ) {
                Icon(Icons.Default.Home, contentDescription = "房間資料管理", modifier = Modifier.padding(end = 8.dp))
                Text("房間資料管理", style = MaterialTheme.typography.bodyLarge)
            }
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("electricity/landlord") }
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = "電表計算頁面", modifier = Modifier.padding(end = 8.dp))
                Text("電表計算頁面", style = MaterialTheme.typography.bodyLarge)
            }
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("electricity_query") }
            ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = "歷史電費查詢", modifier = Modifier.padding(end = 8.dp))
                Text("歷史電費查詢", style = MaterialTheme.typography.bodyLarge)
            }
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("excel_import") }
            ) {
                Icon(Icons.Default.NoteAdd, contentDescription = "匯入 Excel 資料", modifier = Modifier.padding(end = 8.dp))
                Text("匯入 Excel 資料", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
