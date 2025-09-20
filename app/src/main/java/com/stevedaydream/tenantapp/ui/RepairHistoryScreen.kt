@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RepairReport
import com.stevedaydream.tenantapp.data.RepairReportDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RepairHistoryScreen(
    navController: NavHostController,
    dao: RepairReportDao,
    // 【核心修改】傳入使用者角色，判斷是否為房東
    isLandlord: Boolean
) {
    val reports by dao.getAll().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var showDetailDialog by remember { mutableStateOf<RepairReport?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歷史修繕回報", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (reports.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "目前沒有任何修繕紀錄",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reports) { report ->
                    ReportHistoryCard(report = report, dateFormat = dateFormat, onClick = {
                        showDetailDialog = report
                    })
                }
            }
        }
    }

    // Dialog for showing details and allowing landlord to edit status
    if (showDetailDialog != null) {
        val report = showDetailDialog!!
        var selectedStatus by remember { mutableStateOf(report.status) }

        AlertDialog(
            onDismissRequest = { showDetailDialog = null },
            title = { Text("${report.roomNumber}房 - ${report.issue}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // --- 【*** 核心修正點：顯示完整資訊 ***】 ---
                    Text("回報人: ${report.tenantName}", style = MaterialTheme.typography.bodyLarge)
                    Text("回報時間: ${dateFormat.format(Date(report.date))}", style = MaterialTheme.typography.bodyMedium)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("問題描述:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(report.description, style = MaterialTheme.typography.bodyLarge)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    // --- 【修正結束】 ---

                    // 如果是房東，顯示狀態選擇器
                    if (isLandlord) {
                        Text("更新處理狀態:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val statusOptions = listOf("待處理", "處理中", "已完成")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            statusOptions.forEach { option ->
                                FilterChip(
                                    selected = selectedStatus == option,
                                    onClick = { selectedStatus = option },
                                    label = { Text(option) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 如果是房東且狀態有改變，就更新資料庫
                        if (isLandlord && selectedStatus != report.status) {
                            scope.launch {
                                val updatedReport = report.copy(status = selectedStatus)
                                dao.update(updatedReport)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "狀態已更新", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showDetailDialog = null
                    }
                ) { Text(if (isLandlord) "儲存" else "關閉") }
            },
            dismissButton = {
                // 只有租客才需要額外的取消按鈕
                if (!isLandlord) {
                    TextButton(onClick = { showDetailDialog = null }) { Text("取消") }
                }
            }
        )
    }
}

@Composable
private fun ReportHistoryCard(report: RepairReport, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    val statusColor = when (report.status) {
        "已完成" -> Color.Gray
        "處理中" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Engineering,
                        contentDescription = "修繕問題",
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "${report.roomNumber} - ${report.issue}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = report.status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(statusColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Divider()
            Text(
                text = report.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "回報人: ${report.tenantName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "時間: ${dateFormat.format(Date(report.date))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}