package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.Announcement
import com.stevedaydream.tenantapp.data.AnnouncementDao
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreen(
    dao: AnnouncementDao,
    onNavigateBack: () -> Unit,
    currentUser: User?, // 傳入當前使用者
    landlordCode: String? // 傳入房東 Code
) {
    // 根據使用者角色判斷是否有編輯權限
    val canEdit = currentUser?.role == "landlord" || currentUser?.role == "admin" // 假設有 admin 角色

    // 根據使用者角色和綁定的房東 Code 決定要看哪些公告
    val announcements by remember(currentUser?.boundLandlordCode) {
        when {
            // 房客：看全域公告和自己房東的公告
            currentUser?.role == "tenant" && currentUser.boundLandlordCode != null ->
                dao.getGlobalAndByLandlordCode(currentUser.boundLandlordCode!!)
            // 房東：看全域公告和自己的公告
            currentUser?.role == "landlord" && currentUser.landlordCode != null ->
                dao.getGlobalAndByLandlordCode(currentUser.landlordCode)
            // 預設或訪客：看所有公告
            else -> dao.getAll()
        }
    }.collectAsState(initial = emptyList())


    val scope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf<Announcement?>(null) } // 用來顯示詳細內容的 Dialog
    var editing: Announcement? by remember { mutableStateOf(null) }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("最新公告", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            // 只有房東和管理員能看到新增按鈕
            if (canEdit) {
                ElevatedButton(
                    onClick = {
                        editing = null
                        showEditDialog = true
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新增公告", modifier = Modifier.padding(end = 8.dp))
                    Text("新增公告")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (announcements.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "目前沒有任何公告",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(announcements) { ann ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 點擊卡片，打開詳細內容 Dialog
                                    showDetailDialog = ann
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = ann.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ann.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "發布時間: ${dateFormat.format(Date(ann.date))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 詳細內容 Dialog
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
            },
            dismissButton = {
                // 只有房東且是自己發的公告，或管理員才能編輯
                val canEditThis = canEdit && (showDetailDialog!!.landlordCode == landlordCode || showDetailDialog!!.landlordCode == null)
                if (canEditThis) {
                    TextButton(onClick = {
                        editing = showDetailDialog
                        showDetailDialog = null
                        showEditDialog = true
                    }) { Text("編輯") }
                }
            }
        )
    }


    // 編輯/新增 Dialog
    if (showEditDialog) {
        var title by remember { mutableStateOf(editing?.title ?: "") }
        var content by remember { mutableStateOf(editing?.content ?: "") }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(if (editing == null) "新增公告" else "編輯公告", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("標題") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "標題") }
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("內容") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = "內容") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            if (editing == null) {
                                // 新增時，寫入 landlordCode
                                dao.insert(Announcement(title = title, content = content, landlordCode = landlordCode))
                            } else {
                                dao.update(editing!!.copy(title = title, content = content))
                            }
                            showEditDialog = false
                        }
                    },
                    enabled = title.isNotBlank() && content.isNotBlank()
                ) {
                    Text("儲存")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editing != null) {
                        Button(
                            onClick = {
                                scope.launch { dao.delete(editing!!) }
                                showEditDialog = false
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "刪除", modifier = Modifier.padding(end = 4.dp))
                            Text("刪除")
                        }
                    }
                    Button(onClick = { showEditDialog = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}