@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AnnouncementDao
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.User

// 保持與原程式碼相同的 LoginState
object LoginState {
    var currentUser: User? = null
}

@Composable
fun VisitorHomeScreen(
    onNavigate: (String) -> Unit,
    announcementDao: AnnouncementDao,
    roomDao: RoomDao,
    navController: NavHostController
) {
    val announcements by announcementDao.getAll().collectAsState(initial = emptyList())
    val rooms by roomDao.getAllRooms().collectAsState(initial = emptyList())
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        // 上方導航列保持不變，但標題字體更為突出
        topBar = {
            TopAppBar(
                title = {
                    Text("租屋系統", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "選單")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("返回") },
                            onClick = {
                                menuExpanded = false
                                navController.popBackStack()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回 Icon")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("登入") },
                            onClick = {
                                menuExpanded = false
                                navController.navigate("login")
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = "登入 Icon")
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            // 使用 spacedBy 統一間距
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 優化「最新公告」區塊的標題與卡片
            Text(
                "📢 最新公告",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (announcements.isEmpty()) {
                        Text("目前沒有公告", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        announcements.take(3).forEach {
                            Text(
                                it.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                it.content,
                                maxLines = 2,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Divider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }

            // 優化「可租房間」區塊的標題與卡片
            Text(
                "🏠 可租房間",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            val availableRooms = rooms.filter { it.status.contains("可租", ignoreCase = true) }
            if (availableRooms.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "目前沒有可租房間",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 使用 forEach 迴圈建立多個精緻的房間卡片
                availableRooms.forEach {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "房號: ${it.roomNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "房型: ${it.type} / 租金: ${it.rentAmount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 使用 ElevatedButton 增加視覺效果，並加入 Icon
            ElevatedButton(
                onClick = {
                    val user = LoginState.currentUser
                    if (user == null) {
                        onNavigate("login")
                    } else {
                        if (user.role == "tenant") {
                            onNavigate("tenant_electricity")
                        } else if (user.role == "landlord") {
                            onNavigate("landlord_electricity")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                // 優化按鈕的 Icon 和文字排版
                Icon(Icons.Default.FlashOn, contentDescription = "查詢電費", modifier = Modifier.padding(end = 8.dp))
                Text("查詢當月電費", style = MaterialTheme.typography.bodyLarge)
            }

            // 使用 ElevatedButton 增加視覺效果，並加入 Icon
            ElevatedButton(
                onClick = {
                    val user = LoginState.currentUser
                    if (user == null) {
                        onNavigate("login")
                    } else {
                        if (user.role == "tenant") {
                            onNavigate("tenant_pay")
                        } else if (user.role == "landlord") {
                            onNavigate("landlord_pay")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                // 優化按鈕的 Icon 和文字排版
                Icon(Icons.Default.MonetizationOn, contentDescription = "我要繳費", modifier = Modifier.padding(end = 8.dp))
                Text("我要繳費", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
