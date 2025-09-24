package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AdminRepository
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.User
import com.stevedaydream.tenantapp.ui.shared.RoomEditDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRoomListScreen(
    navController: NavHostController,
    adminRepository: AdminRepository
) {
    val viewModel: AdminRoomListViewModel = viewModel(factory = AdminRoomListViewModelFactory(adminRepository))
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 監聽錯誤訊息並顯示 Toast
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    // 顯示編輯/新增對話框
    if (uiState.showEditDialog && uiState.editingRoom != null) {
        RoomEditDialog(
            room = uiState.editingRoom!!,
            isNew = uiState.isCreatingNew,
            allLandlords = uiState.allLandlords,
            onDismiss = { viewModel.onDismissDialog() },
            onSave = { room,imageUris-> viewModel.onSaveRoom(room) },
            onDelete = { room -> viewModel.onDeleteRoom(room) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("所有房間列表") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onAddNewRoomClicked() }) {
                Icon(Icons.Default.Add, contentDescription = "新增房間")
            }
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.roomGroups.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.roomGroups.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text("雲端上沒有任何房間資料")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.roomGroups[null]?.let { unassignedRooms ->
                        item {
                            LandlordRoomGroup(
                                landlordName = "未指派房東",
                                landlordInfo = "(${unassignedRooms.size} 間)",
                                rooms = unassignedRooms,
                                onRoomClick = { room -> viewModel.onEditRoomClicked(room) }
                            )
                        }
                    }

                    val sortedLandlords = uiState.roomGroups.keys.filterNotNull().sortedBy { it.username }
                    sortedLandlords.forEach { landlord ->
                        uiState.roomGroups[landlord]?.let { rooms ->
                            item {
                                LandlordRoomGroup(
                                    landlordName = landlord.username,
                                    landlordInfo = "(${landlord.landlordCode})",
                                    rooms = rooms,
                                    onRoomClick = { room -> viewModel.onEditRoomClicked(room) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandlordRoomGroup(
    landlordName: String,
    landlordInfo: String,
    rooms: List<RoomEntity>,
    onRoomClick: (RoomEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(landlordName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(landlordInfo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收合" else "展開"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    rooms.sortedBy { it.roomNumber }.forEach { room ->
                        RoomInfoRow(room, onClick = { onRoomClick(room) })
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomInfoRow(room: RoomEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("房號: ${room.roomNumber}", fontWeight = FontWeight.SemiBold)
            Text("租客: ${room.tenantName.ifBlank { "無" }}", style = MaterialTheme.typography.bodySmall)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(room.status, color = when(room.status) {
                "可租" -> MaterialTheme.colorScheme.primary
                "出租中" -> MaterialTheme.colorScheme.error
                else -> LocalContentColor.current
            })
            Text("${room.rentAmount} 元/月", style = MaterialTheme.typography.bodySmall)
        }
    }
}