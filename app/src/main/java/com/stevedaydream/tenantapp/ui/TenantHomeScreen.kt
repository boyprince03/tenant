@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.stevedaydream.tenantapp.ui


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "歡迎使用租客APP",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            // 優化後的公告卡片
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

            // 優化功能按鈕，使用 ElevatedButton 和 Icon
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("home") }
            ) {
                Icon(Icons.Default.Engineering, contentDescription = "修繕回報", modifier = Modifier.padding(end = 8.dp))
                Text("前往填寫修繕回報", style = MaterialTheme.typography.bodyLarge)
            }
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("electricity") }
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = "電表計算", modifier = Modifier.padding(end = 8.dp))
                Text("電表計算頁面", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
