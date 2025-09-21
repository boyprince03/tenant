package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomChangeApprovalScreen(
    landlordId: String,
    db: AppDatabase,
    navController: NavHostController,
    // 【*** 核心修改 1：接收所有需要的 Repository ***】
    userRepository: UserRepository,
    roomRepository: RoomRepository,
    requestRepository: RoomChangeRequestRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userDao = db.userDao() // 仍然可以用來讀取本地快取的使用者資料

    var landlord by remember { mutableStateOf<User?>(null) }
    // 【*** 核心修改 2：從 Repository 監聽資料 ***】
    val requests by remember(landlord) {
        landlord?.landlordCode?.let { requestRepository.getRequestsByLandlord(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    var selectedRequest by remember { mutableStateOf<RoomChangeRequest?>(null) }

    LaunchedEffect(landlordId) {
        landlord = userDao.getUserById(landlordId)
    }

    if (selectedRequest != null) {
        ApprovalDialog(
            request = selectedRequest!!,
            onDismiss = { selectedRequest = null },
            onConfirm = { req, isApproved ->
                scope.launch {
                    val newStatus = if (isApproved) "approved" else "rejected"
                    val updatedRequest = req.copy(status = newStatus)

                    // 【*** 核心修改 3：使用 Repository 更新請求狀態 ***】
                    requestRepository.update(updatedRequest)

                    if (isApproved) {
                        // 1. 找到房客 User (使用 userRepository)
                        val tenant = userRepository.getUser(req.tenantId)
                        // 2. 找到舊房間和新房間 (用本地 dao 讀取是可以的)
                        val oldRoom = db.roomDao().getRoomByNumber(req.currentRoomNumber)
                        val newRoom = db.roomDao().getRoomByNumber(req.requestedRoomNumber)

                        if (tenant != null && oldRoom != null && newRoom != null) {
                            // 3. 更新房客綁定的房間 (使用 userRepository)
                            userRepository.updateUser(tenant.copy(boundRoomNumber = req.requestedRoomNumber))

                            // 4. 更新舊房間狀態為「可租」 (使用 roomRepository)
                            roomRepository.updateRoom(oldRoom.copy(tenantId = null, tenantName = "", status = "可租"))

                            // 5. 更新新房間狀態為「出租中」並綁定租客 (使用 roomRepository)
                            roomRepository.updateRoom(newRoom.copy(tenantId = tenant.id, tenantName = tenant.username, status = "出租中"))

                            Toast.makeText(context, "已同意並更新雲端與本地資料", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "更新失敗：找不到相關資料", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "已拒絕該請求", Toast.LENGTH_SHORT).show()
                    }
                }
                selectedRequest = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("審核房間更換請求") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (requests.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("目前沒有任何請求紀錄")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(requests, key = { it.id }) { request ->
                    RequestCard(request = request, onClick = { selectedRequest = request })
                }
            }
        }
    }
}

@Composable
private fun RequestCard(request: RoomChangeRequest, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val (statusText, statusColor) = when (request.status) {
        "approved" -> "已核准" to Color.Green.copy(alpha = 0.7f)
        "rejected" -> "已拒絕" to Color.Red.copy(alpha = 0.7f)
        else -> "待審核" to MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(enabled = request.status == "pending") { onClick() }) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "請求人: ${request.tenantName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    statusText,
                    color = Color.White,
                    modifier = Modifier
                        .background(statusColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text("更換房間: 從 ${request.currentRoomNumber} 到 ${request.requestedRoomNumber}")
            Text(
                "申請時間: ${dateFormat.format(Date(request.requestDate))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApprovalDialog(
    request: RoomChangeRequest,
    onDismiss: () -> Unit,
    onConfirm: (RoomChangeRequest, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("審核請求") },
        text = {
            Column {
                Text("房客 ${request.tenantName} 申請將房間從 ${request.currentRoomNumber} 更換至 ${request.requestedRoomNumber}。")
                Spacer(Modifier.height(8.dp))
                Text("是否同意此請求？")
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(request, true) }) { Text("同意") }
        },
        dismissButton = {
            Row {
                Button(
                    onClick = { onConfirm(request, false) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("拒絕") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
