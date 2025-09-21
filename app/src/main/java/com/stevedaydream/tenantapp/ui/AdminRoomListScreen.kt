package com.stevedaydream.tenantapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AdminRepository
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRoomListScreen(
    navController: NavHostController,
    adminRepository: AdminRepository
) {
    val viewModel: AdminRoomListViewModel = viewModel(factory = AdminRoomListViewModelFactory(adminRepository))
    val uiState by viewModel.uiState.collectAsState()

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
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
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
                    // 將未指派的房間顯示在最上面
                    uiState.roomGroups[null]?.let { unassignedRooms ->
                        item {
                            LandlordRoomGroup(
                                landlordName = "未指派房東",
                                landlordInfo = "(${unassignedRooms.size} 間)",
                                rooms = unassignedRooms
                            )
                        }
                    }

                    // 排序其他房東
                    val sortedLandlords = uiState.roomGroups.keys.filterNotNull().sortedBy { it.username }
                    sortedLandlords.forEach { landlord ->
                        uiState.roomGroups[landlord]?.let { rooms ->
                            item {
                                LandlordRoomGroup(
                                    landlordName = landlord.username,
                                    landlordInfo = "(${landlord.landlordCode})",
                                    rooms = rooms
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
    rooms: List<RoomEntity>
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
                        RoomInfoRow(room)
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomInfoRow(room: RoomEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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