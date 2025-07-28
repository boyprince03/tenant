@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RepairReportDao
import java.util.Date

@Composable
fun HistoryScreen(navController: NavHostController, dao: RepairReportDao) {
    val reports by dao.getAll().collectAsState(initial = emptyList())

    var expanded by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歷史修繕回報") },
                actions = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "選單")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("首頁") },
                            onClick = {
                                expanded = false
                                navController.navigate("mainhome")
                            }
                        )
                        // 其他選單...
                    }
                }
            )
        }
    ) { innerPadding ->
        // 原有內容放這裡
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
//                Text("歷史修繕回報", style = MaterialTheme.typography.headlineMedium)
                LazyColumn {
                    items(reports) { report ->
                        Text("${report.tenantName} / ${report.roomNumber} / ${report.issue}\n${report.description}\n${Date(report.date)}")
                        Divider()
                    }
                }
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("返回") }
            }
        }
}






