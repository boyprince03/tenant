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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PointOfSale
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.stevedaydream.tenantapp.data.AppDatabase
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LandlordHomeScreen(
    landlordCode: String,
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit // 新增登出回呼
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val announcementDao = db.announcementDao()
    val repairReportDao = db.repairReportDao()
    val announcements by announcementDao.getAll().collectAsState(initial = emptyList())
    val repairReports by repairReportDao.getAll().collectAsState(initial = emptyList())

    var expanded by remember { mutableStateOf(false) }
    var codeVisible by remember { mutableStateOf(false) }
    fun maskCode(code: String): String {
        return if (code.length <= 4) "*".repeat(code.length)
        else code.take(2) + "*".repeat(code.length - 4) + code.takeLast(2)
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN)


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
                        // 【核心修改】新增登出按鈕
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 房東序號顯示區
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
                                Text(
                                    "房號: ${report.roomNumber} - ${report.issue}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                        onClick = { onNavigate("repair_report_list") },
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
                onClick = { onNavigate("excel_import") }
            ) {
                Icon(Icons.Default.NoteAdd, contentDescription = "匯入 Excel 資料", modifier = Modifier.padding(end = 8.dp))
                Text("匯入 Excel 資料", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}