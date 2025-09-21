package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectRoomScreen(
    userId: String,
    userRepository: UserRepository,
    roomRepository: RoomRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var landlordCodeInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    var availableRooms by remember { mutableStateOf<List<RoomEntity>>(emptyList()) }
    var showRoomSelectionDialog by remember { mutableStateOf(false) }

    var currentUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }


    LaunchedEffect(userId) {
        userRepository.getUser(userId).collect { user ->
            currentUser = user
            isLoading = false
        }
    }

    if (showRoomSelectionDialog) {
        RoomSelectionDialog(
            rooms = availableRooms,
            onDismiss = { showRoomSelectionDialog = false },
            onRoomSelected = { room ->
                showRoomSelectionDialog = false
                scope.launch {
                    val userToUpdate = currentUser
                    if (userToUpdate != null) {
                        try {
                            // 1. 更新使用者資訊
                            val updatedUser = userToUpdate.copy(
                                boundLandlordCode = room.landlordCode,
                                boundRoomNumber = room.roomNumber
                            )
                            userRepository.saveUser(updatedUser)

                            // 2. 更新房間資訊
                            val updatedRoom = room.copy(
                                tenantId = userToUpdate.id,
                                tenantName = userToUpdate.username,
                                status = "出租中"
                            )
                            roomRepository.updateRoom(updatedRoom)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "成功綁定 ${room.roomNumber} 房！", Toast.LENGTH_SHORT).show()
                                onNavigateBack() // 綁定成功後返回上一頁
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "綁定失敗: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("綁定房東與房間") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("請輸入房東提供給您的「房東序號」以尋找可租用的房間。")
                OutlinedTextField(
                    value = landlordCodeInput,
                    onValueChange = { landlordCodeInput = it },
                    label = { Text("房東序號") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMsg.isNotEmpty()
                )
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        if (landlordCodeInput.isBlank()) {
                            errorMsg = "請輸入房東序號"
                            return@Button
                        }
                        isSearching = true
                        errorMsg = ""
                        scope.launch {
                            val rooms = roomRepository.getAvailableRoomsForLandlord(landlordCodeInput)
                            if (rooms.isNotEmpty()) {
                                availableRooms = rooms
                                showRoomSelectionDialog = true
                            } else {
                                errorMsg = "找不到此房東或此房東目前無可租房間"
                            }
                            isSearching = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSearching
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text("尋找房間")
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomSelectionDialog(
    rooms: List<RoomEntity>,
    onDismiss: () -> Unit,
    onRoomSelected: (RoomEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("請選擇要綁定的房間") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rooms, key = { it.id }) { room ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRoomSelected(room) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "房號: ${room.roomNumber}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("房型: ${room.type}", style = MaterialTheme.typography.bodyMedium)
                            Text("租金: ${room.rentAmount}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
