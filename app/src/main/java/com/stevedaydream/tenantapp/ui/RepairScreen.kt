@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RepairReport
import com.stevedaydream.tenantapp.data.RepairReportDao
import kotlinx.coroutines.launch

@Composable
fun RepairScreen(navController: NavHostController, dao: RepairReportDao) {
    var tenantName by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var issue by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("填寫修繕回報") },
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
                Text("租客修繕回報", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("姓名") })
                OutlinedTextField(value = roomNumber, onValueChange = { roomNumber = it }, label = { Text("房號") })
                OutlinedTextField(value = issue, onValueChange = { issue = it }, label = { Text("問題類型") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("說明") })
                Button(
                    onClick = {
                        scope.launch {
                            dao.insert(
                                RepairReport(
                                    tenantName = tenantName,
                                    roomNumber = roomNumber,
                                    issue = issue,
                                    description = description
                                )
                            )
                            // 清空
                            tenantName = ""; roomNumber = ""; issue = ""; description = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text("送出回報") }
                Button(
                    onClick = { navController.navigate("history") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("查看歷史回報") }



            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("返回") }
        }
    }
}




