@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.stevedaydream.tenantapp.ui


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Logout
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
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.launch

@Composable
fun TenantHomeScreen(
    userId: Int,
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit // 新增登出回呼
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val userDao = db.userDao()
    val announcementDao = db.announcementDao()

    var currentUser by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        scope.launch {
            currentUser = userDao.getUserById(userId)
        }
    }

    val announcements by remember(currentUser?.boundLandlordCode) {
        val code = currentUser?.boundLandlordCode
        if (code != null) {
            announcementDao.getGlobalAndByLandlordCode(code)
        } else {
            announcementDao.getGlobalAndByLandlordCode("")
        }
    }.collectAsState(initial = emptyList())


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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "歡迎！ ${currentUser?.username ?: ""}",
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
                        onClick = { onNavigate("announcement/${currentUser?.id ?: 0}") },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp)
                    ) { Text("查看更多公告") }
                }
            }

            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("select_room/$userId") }
            ) {
                Icon(Icons.Default.HomeWork, contentDescription = "綁定房間", modifier = Modifier.padding(end = 8.dp))
                Text("綁定房東及房間", style = MaterialTheme.typography.bodyLarge)
            }

            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("home") }
            ) {
                Icon(Icons.Default.Engineering, contentDescription = "修繕回報", modifier = Modifier.padding(end = 8.dp))
                Text("前往填寫修繕回報", style = MaterialTheme.typography.bodyLarge)
            }
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("electricity/tenant") }
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = "電表計算", modifier = Modifier.padding(end = 8.dp))
                Text("電表計算頁面", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}