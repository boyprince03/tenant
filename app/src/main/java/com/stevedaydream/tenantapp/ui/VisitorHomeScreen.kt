@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.stevedaydream.tenantapp.data.Announcement
import com.stevedaydream.tenantapp.data.RoomEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VisitorHomeScreen(
    navController: NavHostController,
    viewModelFactory: VisitorViewModelFactory
) {
    val viewModel: VisitorViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf<Announcement?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "租屋系統",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    TopBarMenu(
                        onMenuExpanded = { menuExpanded = true },
                        onLoginClick = {
                            menuExpanded = false
                            navController.navigate("login")
                        }
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("登入") },
                            onClick = {
                                menuExpanded = false
                                navController.navigate("login")
                            },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "登入") }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 公告區塊
                item {
                    AnnouncementsSection(
                        announcements = uiState.announcements,
                        onAnnouncementClick = { announcement ->
                            showDetailDialog = announcement
                        }
                    )
                }

                // 房間列表區塊
                item {
                    AvailableRoomsSection(rooms = uiState.availableRooms)
                }
            }
        }
    }

    // 公告詳細內容彈窗
    if (showDetailDialog != null) {
        AnnouncementDetailDialog(
            announcement = showDetailDialog!!,
            onDismiss = { showDetailDialog = null }
        )
    }
}

// --- 重構後拆分出的子元件 ---

@Composable
private fun TopBarMenu(onMenuExpanded: () -> Unit, onLoginClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "選單")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("登入") },
                onClick = {
                    expanded = false
                    onLoginClick()
                },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "登入 Icon") }
            )
        }
    }
}

@Composable
private fun AnnouncementsSection(
    announcements: List<Announcement>,
    onAnnouncementClick: (Announcement) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "📢 最新公告",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            if (announcements.isEmpty()) {
                InfoCard(text = "目前沒有任何公告")
            } else {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    announcements.take(3).forEach { announcement ->
                        AnnouncementItem(
                            announcement = announcement,
                            onClick = { onAnnouncementClick(announcement) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableRoomsSection(rooms: List<RoomEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "🏠 可租房間",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (rooms.isEmpty()) {
            InfoCard(text = "目前沒有可租房間")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rooms.forEach { room ->
                    RoomItemCard(room = room)
                }
            }
        }
    }
}

@Composable
private fun AnnouncementItem(announcement: Announcement, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = announcement.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "發布於: ${dateFormat.format(Date(announcement.date))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoomItemCard(room: RoomEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            if (room.imageUrls.isNotEmpty()) {
                ImageCarousel(imageUrls = room.imageUrls)
            }
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "房號: ${room.roomNumber} (${room.type})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "租金: ${room.rentAmount} 元/月",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (room.note.isNotBlank()) {
                    Text(
                        text = "備註: ${room.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCarousel(imageUrls: List<String>) {
    val pagerState = rememberPagerState(pageCount = { imageUrls.size })

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) { page ->
        AsyncImage(
            model = imageUrls[page],
            contentDescription = "房間圖片 ${page + 1}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InfoCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnnouncementDetailDialog(announcement: Announcement, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(announcement.title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                item {
                    Text(announcement.content, style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("關閉") }
        }
    )
}