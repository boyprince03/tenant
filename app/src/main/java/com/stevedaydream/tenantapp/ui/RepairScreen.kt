// tenantapp/ui/RepairScreen.kt

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Corrected import
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.data.RepairReportRepository

@Composable
fun RepairScreen(
    navController: NavHostController,
    repairReportRepository: RepairReportRepository, // Updated: Pass repository
    db: AppDatabase, // Pass db to get UserDao for the factory
    userId: String
) {
    val factory = RepairViewModelFactory(repairReportRepository, db.userDao(), userId)
    val viewModel: RepairViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current

    // Observe submission status for Toast messages
    LaunchedEffect(uiState.submissionStatus) {
        uiState.submissionStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSubmissionStatus() // Clear status after showing
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("填寫修繕回報", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        // Use AutoMirrored Icon for ArrowBack
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                        value = uiState.tenantName,
                        onValueChange = { /* No-op, field is disabled */ },
                        label = { Text("您的姓名") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "姓名") },
                        enabled = false
                    )
                    OutlinedTextField(
                        value = uiState.roomNumber,
                        onValueChange = { /* No-op, field is disabled */ },
                        label = { Text("房號") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = "房號") },
                        enabled = false
                    )
                    OutlinedTextField(
                        value = uiState.issue,
                        onValueChange = { viewModel.onIssueChanged(it) },
                        label = { Text("問題類型 (如：電燈故障、水管不通)") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = "問題類型") },
                        isError = uiState.submissionStatus?.contains("欄位") == true && uiState.issue.isBlank() // Highlight if empty on specific error
                    )
                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = { viewModel.onDescriptionChanged(it) },
                        label = { Text("詳細說明") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = "詳細說明") },
                        minLines = 3,
                        isError = uiState.submissionStatus?.contains("欄位") == true && uiState.description.isBlank() // Highlight if empty on specific error
                    )
                }
            }

            Button(
                onClick = { viewModel.submitReport() },
                enabled = !uiState.isSubmitting, // Disable button when submitting
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Send, contentDescription = "送出", modifier = Modifier.padding(end = 8.dp))
                    Text("送出回報")
                }
            }

            OutlinedButton(
                onClick = { navController.navigate("history/$userId") }, // userId from parameter
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.History, contentDescription = "歷史紀錄", modifier = Modifier.padding(end = 8.dp))
                Text("查看歷史回報")
            }
        }
    }
}