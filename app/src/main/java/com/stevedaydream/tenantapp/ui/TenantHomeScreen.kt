@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.stevedaydream.tenantapp.ui


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.AppDatabase




@Composable
fun TenantHomeScreen(
    onNavigate: (String) -> Unit = {}
) {
    // 取得資料庫 & DAO
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val announcementDao = db.announcementDao()
    val announcements by announcementDao.getAll().collectAsState(initial = emptyList())

    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("租客系統") },
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
                        // 可以繼續加選單項目
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text("歡迎使用租客APP", style = MaterialTheme.typography.headlineMedium)
            // 最新公告區
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("📢 最新公告", style = MaterialTheme.typography.titleMedium)
                    if (announcements.isEmpty()) {
                        Text("目前沒有公告")
                    } else {
                        announcements.take(3).forEach {
                            Text(it.title, style = MaterialTheme.typography.bodyMedium)
                            Text(it.content, maxLines = 2)
                            Divider()
                        }
                    }
                    TextButton(
                        onClick = { onNavigate("announcement") },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("查看更多公告") }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("home") }
            ) {
                Text("前往填寫修繕回報")
            }
            // 新增這段
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("contract") }   // 跳轉到合約頁面
            ) {
                Text("產生電子合約（PDF）")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("room_manage") }
            ) { Text("房間資料管理") }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("electricity") }
            ) { Text("電表計算頁面") }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("excel_import") }
            ) {
                Text("匯入 Excel 資料")
            }


        }
    }
}
