@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.stevedaydream.tenantapp.ui


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.LocalContentColor
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
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TenantHomeScreen(
    userId: Int,
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val userDao = db.userDao()
    val announcementDao = db.announcementDao()
    val roomDao = db.roomDao()
    val paymentDao = db.paymentDao()

    var currentUser by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()

    // --- 新增狀態 ---
    var showRoomInfoDialog by remember { mutableStateOf(false) }
    var landlord by remember { mutableStateOf<User?>(null) }
    var roomDetails by remember { mutableStateOf<RoomEntity?>(null) }
    var paymentStatus by remember { mutableStateOf("查詢中...") }


    LaunchedEffect(userId) {
        scope.launch(Dispatchers.IO) {
            val user = userDao.getUserById(userId)
            withContext(Dispatchers.Main) {
                currentUser = user
            }
            if (user?.boundRoomNumber != null && user.boundLandlordCode != null) {
                val room = roomDao.getAllRooms().firstOrNull()?.find { it.roomNumber == user.boundRoomNumber }
                val landlordUser = userDao.getLandlordByCode(user.boundLandlordCode!!)
                val payment = paymentDao.getPaymentRecord(user.boundRoomNumber!!, "2025-07").firstOrNull() // 這裡應使用當前月份

                withContext(Dispatchers.Main) {
                    roomDetails = room
                    landlord = landlordUser
                    paymentStatus = if (payment?.isPaid == true) "已繳清" else "未繳費"
                }
            }
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
                .verticalScroll(rememberScrollState()) // <-- 加入捲動
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "歡迎！ ${currentUser?.username ?: ""}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            // 公告卡片 (保持不變)
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

            // --- 新增按鈕 ---
            if (currentUser?.boundRoomNumber != null) {
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showRoomInfoDialog = true }
                ) {
                    Icon(Icons.Default.Info, contentDescription = "房間資訊", modifier = Modifier.padding(end = 8.dp))
                    Text("房間基本資訊", style = MaterialTheme.typography.bodyLarge)
                }
            }


            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("tenant_payment/$userId") }
            ) {
                Icon(Icons.Default.MonetizationOn, contentDescription = "繳費查詢", modifier = Modifier.padding(end = 8.dp))
                Text("當月繳費查詢", style = MaterialTheme.typography.bodyLarge)
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
                // --- 【*** 核心修改：更新 onClick ***】 ---
                onClick = { onNavigate("electricity_query/$userId") }
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = "電費查詢", modifier = Modifier.padding(end = 8.dp))
                Text("歷史電費查詢", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // --- 新增 Dialog ---
        if (showRoomInfoDialog) {
            RoomInfoDialog(
                room = roomDetails,
                landlord = landlord,
                tenant = currentUser,
                paymentStatus = paymentStatus,
                onDismiss = { showRoomInfoDialog = false }
            )
        }
    }
}

// --- 新增 Composable 函數 ---
@Composable
fun RoomInfoDialog(
    room: RoomEntity?,
    landlord: User?,
    tenant: User?,
    paymentStatus: String,
    onDismiss: () -> Unit
) {
    if (room == null || landlord == null || tenant == null) {
        // 可以顯示一個載入中或錯誤的提示
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("讀取中...") },
            text = { Text("正在取得房間詳細資訊，請稍候。") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("關閉") }
            }
        )
        return
    }

    val depositMonths = if (room.rentAmount > 0) "(${room.deposit / room.rentAmount} 個月)" else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "房間基本資訊", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow("房屋地址:", room.address)
                InfoRow("房間樓層:", room.floor)
                InfoRow("房間號:", room.roomNumber)
                InfoRow("坪數:", room.size)
                InfoRow("房型:", room.type)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                InfoRow("出租人:", "${landlord.username} / ${landlord.phone}")
                InfoRow("承租人:", tenant.username)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                InfoRow("月租金:", "${room.rentAmount} 元")
                InfoRow("押金:", "${room.deposit} 元 ${depositMonths}")
                InfoRow("租賃期間:", "${room.rentStartDate} ~ ${room.rentEndDate} (共 ${room.rentDuration})")
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                InfoRow("繳費方式:", "每月 5 日前轉帳")
                InfoRow("房東戶名:", landlord.bankAccountName ?: "未提供")
                InfoRow("房東帳號:", landlord.bankAccountNumber ?: "未提供")
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                InfoRow("當月繳費狀態:", paymentStatus, isHighlight = true)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            fontWeight = if(isHighlight) FontWeight.Bold else FontWeight.Normal
        )
    }
}