package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AdminRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTestDataScreen(
    navController: NavHostController,
    adminRepository: AdminRepository
) {
    val viewModel: AdminTestDataViewModel = viewModel(factory = AdminTestDataViewModelFactory(adminRepository))
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("產生測試資料") },
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("正在產生並寫入資料...")
                    }
                }
            }

            if (uiState.message.isNotEmpty()) {
                Text(
                    text = uiState.message,
                    color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            GeneratorCard(
                title = "使用者 (Users)",
                count = viewModel.userCount,
                onCountChange = { viewModel.userCount = it },
                onGenerate = { viewModel.generateUsers() }
            )

            GeneratorCard(
                title = "房間 (Rooms)",
                count = viewModel.roomCount,
                onCountChange = { viewModel.roomCount = it },
                onGenerate = { viewModel.generateRooms() }
            )

            GeneratorCard(
                title = "公告 (Announcements)",
                count = viewModel.announcementCount,
                onCountChange = { viewModel.announcementCount = it },
                onGenerate = { viewModel.generateAnnouncements() }
            )

            GeneratorCard(
                title = "修繕回報 (Repair Reports)",
                count = viewModel.repairReportCount,
                onCountChange = { viewModel.repairReportCount = it },
                onGenerate = { viewModel.generateRepairReports() }
            )
        }
    }
}

@Composable
private fun GeneratorCard(
    title: String,
    count: String,
    onCountChange: (String) -> Unit,
    onGenerate: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = count,
                    onValueChange = onCountChange,
                    label = { Text("數量") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onGenerate) {
                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("產生")
                }
            }
        }
    }
}