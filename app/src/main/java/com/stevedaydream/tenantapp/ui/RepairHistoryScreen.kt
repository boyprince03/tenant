@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RepairReport
import com.stevedaydream.tenantapp.data.RepairReportDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RepairHistoryScreen(navController: NavHostController, dao: RepairReportDao) {
    val reports by dao.getAll().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    // 【*** 核心修改 1/3 ***】新增一個 state 來控制 Dialog 的顯示和內容
    var showDetailDialog by remember { mutableStateOf<RepairReport?>(null) }

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
                    // 【*** 核心修改 2/3 ***】讓卡片可以被點擊，並觸發 Dialog
                    ReportHistoryCard(report = report, dateFormat = dateFormat, onClick = {
                        showDetailDialog = report
                    })
                }
            }
        }
    }

    // 【*** 核心修改 3/3 ***】新增詳細資訊 Dialog
    if (showDetailDialog != null) {
        val report = showDetailDialog!!
        AlertDialog(
            onDismissRequest = { showDetailDialog = null },
            title = { Text("${report.roomNumber}房 - ${report.issue}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "詳細描述:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = report.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Divider()
                    Text(
                        "回報人: ${report.tenantName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "時間: ${dateFormat.format(Date(report.date))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showDetailDialog = null }) {
                    Text("關閉")
                }
            }
        )
    }
}

@Composable
private fun ReportHistoryCard(report: RepairReport, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    Card(
        // Card 本身就可以設定 clickable
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Engineering,
                    contentDescription = "問題類型",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${report.roomNumber}房 - ${report.issue}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Divider()
            Text(
                text = report.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp),
                // 限制預覽行數
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