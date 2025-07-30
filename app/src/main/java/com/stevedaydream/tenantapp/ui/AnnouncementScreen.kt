package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.Announcement
import com.stevedaydream.tenantapp.data.AnnouncementDao
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun AnnouncementScreen(dao: AnnouncementDao) {
    val announcements by dao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var editing: Announcement? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("最新公告管理") }) }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).padding(16.dp)) {
            Button(onClick = { editing = null; showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("新增公告")
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(announcements) { ann ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { editing = ann; showDialog = true }
                    ){
                        Column(Modifier.padding(12.dp)) {
                            Text(ann.title, style = MaterialTheme.typography.titleMedium)
                            Text(ann.content, maxLines = 2)
                            Text(
                                SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(ann.date)),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var title by remember { mutableStateOf(editing?.title ?: "") }
        var content by remember { mutableStateOf(editing?.content ?: "") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editing == null) "新增公告" else "編輯公告") },
            text = {
                Column {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("標題") })
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("內容") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        if (editing == null) {
                            dao.insert(Announcement(title = title, content = content))
                        } else {
                            dao.update(editing!!.copy(title = title, content = content))
                        }
                        showDialog = false
                    }
                }) { Text("儲存") }
            },
            dismissButton = {
                if (editing != null) {
                    Button(onClick = {
                        scope.launch { dao.delete(editing!!) }
                        showDialog = false
                    }) { Text("刪除") }
                }
            }
        )
    }
}
