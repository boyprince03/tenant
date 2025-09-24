package com.stevedaydream.tenantapp.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadTestScreen(
    navController: NavHostController,
    viewModel: UploadTestViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.onFileSelected(uri)
            } else {
                Toast.makeText(context, "未選擇檔案", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("檔案上傳測試", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Text("測試單一檔案上傳至 Firebase Storage。", style = MaterialTheme.typography.bodyLarge)

            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isUploading
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = "選擇檔案", modifier = Modifier.padding(end = 8.dp))
                Text("選擇檔案")
            }

            if (uiState.isUploading) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("上傳中... (${(uiState.progress * 100).toInt()}%)")
            }

            if (uiState.message.isNotEmpty()) {
                Text(
                    text = uiState.message,
                    color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            if (uiState.downloadUrl != null) {
                Spacer(Modifier.height(8.dp))
                Text("檔案已上傳成功！", fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    // TODO: 這裡可以放置開啟網頁或分享連結的邏輯
                    Toast.makeText(context, uiState.downloadUrl, Toast.LENGTH_LONG).show()
                }) {
                    Text("點此複製下載連結")
                }
            }
        }
    }
}