// tenantapp/ui/RepairScreen.kt

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.data.RepairReport
import com.stevedaydream.tenantapp.data.RepairReportDao
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.launch

@Composable
fun RepairScreen(
    navController: NavHostController,
    dao: RepairReportDao,
    db: AppDatabase, // 傳入 db
    userId: Int      // 傳入 userId
) {
    // --- 【核心修改：自動帶入使用者資料】 ---
    var currentUser by remember { mutableStateOf<User?>(null) }
    var tenantName by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        if (userId != 0) {
            currentUser = db.userDao().getUserById(userId)
            currentUser?.let {
                tenantName = it.username
                roomNumber = it.boundRoomNumber ?: ""
            }
        }
    }
    // --- 【修改結束】 ---

    var issue by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("填寫修繕回報", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "請詳細填寫回報資訊",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Divider()
                    OutlinedTextField(
                        value = tenantName,
                        onValueChange = { tenantName = it },
                        label = { Text("您的姓名") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "姓名") },
                        enabled = false // 自動帶入，不允許修改
                    )
                    OutlinedTextField(
                        value = roomNumber,
                        onValueChange = { roomNumber = it },
                        label = { Text("房號") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = "房號") },
                        enabled = false // 自動帶入，不允許修改
                    )
                    OutlinedTextField(
                        value = issue,
                        onValueChange = { issue = it },
                        label = { Text("問題類型 (如：電燈故障、水管不通)") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = "問題類型") }
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("詳細說明") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = "詳細說明") },
                        minLines = 3
                    )
                }
            }

            Button(
                onClick = {
                    if (tenantName.isBlank() || roomNumber.isBlank() || issue.isBlank() || description.isBlank()) {
                        Toast.makeText(context, "請填寫所有欄位", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        dao.insert(
                            RepairReport(
                                tenantName = tenantName,
                                roomNumber = roomNumber,
                                issue = issue,
                                description = description
                            )
                        )
                        Toast.makeText(context, "回報成功！", Toast.LENGTH_SHORT).show()
                        // 清空部分欄位
                        issue = ""
                        description = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "送出", modifier = Modifier.padding(end = 8.dp))
                Text("送出回報")
            }

            OutlinedButton(
                // --- 【核心修改：修正導航路徑】 ---
                onClick = { navController.navigate("history/$userId") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.History, contentDescription = "歷史紀錄", modifier = Modifier.padding(end = 8.dp))
                Text("查看歷史回報")
            }
        }
    }
}