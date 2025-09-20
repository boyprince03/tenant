// tenantapp/ui/TenantHomeScreen.kt

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TenantHomeScreen(
    userId: String, // 【*** 修正：Int -> String ***】
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val userDao = db.userDao()
    val announcementDao = db.announcementDao()
    val roomDao = db.roomDao()
    val paymentDao = db.paymentDao()
    val roomChangeRequestDao = db.roomChangeRequestDao()

    var currentUser by remember { mutableStateOf<User?>(null) }
    val scope = rememberCoroutineScope()

    var showRoomInfoDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf<Announcement?>(null) }
    var landlord by remember { mutableStateOf<User?>(null) }
    var roomDetails by remember { mutableStateOf<RoomEntity?>(null) }
    var paymentStatus by remember { mutableStateOf("查詢中...") }

    val latestRequest by roomChangeRequestDao.getLatestRequestByTenantId(userId)
        .collectAsState(initial = null)


    LaunchedEffect(userId) {
        scope.launch(Dispatchers.IO) {
            val user = userDao.getUserById(userId)
            withContext(Dispatchers.Main) {
                currentUser = user
            }
            if (user?.boundRoomNumber != null && user.boundLandlordCode != null) {
                val room = roomDao.getRoomByNumber(user.boundRoomNumber!!)
                val landlordUser = userDao.getLandlordByCode(user.boundLandlordCode!!)

                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val payment = paymentDao.getPaymentRecord(user.boundRoomNumber!!, currentMonth).firstOrNull()

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
                                onNavigate("history/$userId")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("登出") },
                            leadingIcon = { Icon(Icons.Default.Logout, contentDescription = "登出")},
                            onClick = {
                                expanded = false
                                // 【*** 核心修改 2：這裡的 onLogout 會觸發 AppNavGraph 中的邏輯 ***】
                                // 我們真正修改的地方在 AppNavGraph
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "歡迎！ ${currentUser?.username ?: ""}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                latestRequest?.let { request ->
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
                    if (announcements.isEmpty()) {
                        Text("目前沒有公告", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        announcements.take(3).forEachIndexed { index, ann ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDetailDialog = ann }
                            ) {
                                Text(ann.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(ann.content, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (index < announcements.take(3).size - 1) {
                                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                    TextButton(
                        onClick = { onNavigate("announcement/$userId") }, // 【*** 修正：使用正确的 userId ***】
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

            if (currentUser?.boundRoomNumber != null) {
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigate("request_room_change/$userId") }
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "更換房間", modifier = Modifier.padding(end = 8.dp))
                    Text("申請更換房間", style = MaterialTheme.typography.bodyLarge)
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
                onClick = { onNavigate("select_room/$userId") },
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
                onClick = { onNavigate("home/$userId") }
            ) {
                Icon(Icons.Default.Engineering, contentDescription = "修繕回報", modifier = Modifier.padding(end = 8.dp))
                Text("前往填寫修繕回報", style = MaterialTheme.typography.bodyLarge)
            }
            ElevatedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("electricity_query/$userId") }
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = "電費查詢", modifier = Modifier.padding(end = 8.dp))
                Text("歷史電費查詢", style = MaterialTheme.typography.bodyLarge)
            }
        }

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

// RoomInfoDialog and InfoRow remain unchanged...
@Composable
fun RoomInfoDialog(
    room: RoomEntity?,
    landlord: User?,
    tenant: User?,
    paymentStatus: String,
    onDismiss: () -> Unit
) {
    if (room == null || landlord == null || tenant == null) {
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