package com.stevedaydream.tenantapp.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RoomEntity
import jxl.Workbook
import jxl.write.Label
import jxl.write.WritableWorkbook
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelImportScreen(
    navController: NavHostController,
    viewModel: ExcelImportViewModel,
    userId: String?
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        if (userId != null) {
            viewModel.setCurrentUser(userId)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.parseExcelFile(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Excel 資料匯入", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 步驟一：下載範本
            StepCard(
                step = "1",
                title = "下載範本",
                icon = Icons.Default.Download
            ) {
                Text("請先下載範本檔案，並根據範本格式填寫您的資料。", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { createRoomExcelTemplate(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("下載房間範本")
                    }
                    OutlinedButton(
                        onClick = { createElectricExcelTemplate(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("下載電表範本")
                    }
                }
            }

            // 步驟二：選擇檔案與預覽
            StepCard(
                step = "2",
                title = "選擇檔案與預覽",
                icon = Icons.Default.FileUpload
            ) {
                ElevatedButton(
                    onClick = { launcher.launch(arrayOf("application/vnd.ms-excel")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DriveFolderUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("選擇 Excel 檔案 (.xls)")
                }

                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                }

                if (uiState.message.isNotEmpty()) {
                    Text(
                        uiState.message,
                        color = if (uiState.message.contains("失敗") || uiState.message.startsWith("檔案格式")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // 步驟三：確認與匯入
            if (uiState.previewRows.isNotEmpty()) {
                val nonDuplicateCount = uiState.previewRows.count { it["重複"] != "是" }
                StepCard(
                    step = "3",
                    title = "確認與匯入",
                    icon = Icons.Default.CheckCircle
                ) {
                    Text("偵測到 ${uiState.previewRows.size} 筆資料，其中 ${nonDuplicateCount} 筆為新資料。")

                    // 簡易預覽
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp)
                    ) {
                        uiState.previewRows.forEach { row ->
                            val isDuplicate = row["重複"] == "是"
                            Text(
                                text = row.filterKeys { it != "重複" }.values.joinToString(" | "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDuplicate) Color.Gray else LocalContentColor.current
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.importData() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = nonDuplicateCount > 0 && !uiState.isLoading
                    ) {
                        Icon(Icons.Default.Input, contentDescription = "匯入", modifier = Modifier.padding(end = 8.dp))
                        Text("開始匯入")
                    }
                }
            }
        }
    }
}

/**
 * 【*** 新增 ***】
 * 用於建立 UI 區塊的共用 Composable，已移除 private 關鍵字。
 */
@Composable
private fun StepCard(
    step: String,
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(" $step: $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            content()
        }
    }
}


/**
 * 【*** 新增 ***】
 * 建立房間資料 Excel 範本的輔助函式。
 */
private fun createRoomExcelTemplate(context: Context) {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "房間資料匯入範本.xls")
        val workbook: WritableWorkbook = Workbook.createWorkbook(file)
        val sheet = workbook.createSheet("房間資料", 0)
        val headers = listOf("房號", "租客姓名", "房型", "備註", "租金", "押金", "房屋狀態", "起租日", "結束日", "租賃期間")
        headers.forEachIndexed { index, header ->
            sheet.addCell(Label(index, 0, header))
        }
        // 新增範例資料
        sheet.addCell(Label(0, 1, "A101"))
        sheet.addCell(Label(1, 1, "王小明"))
        sheet.addCell(Label(2, 1, "套房"))
        sheet.addCell(Label(3, 1, "有對外窗"))
        sheet.addCell(Label(4, 1, "8000"))
        sheet.addCell(Label(5, 1, "16000"))
        sheet.addCell(Label(6, 1, "出租中"))
        sheet.addCell(Label(7, 1, "2025-01-01"))
        sheet.addCell(Label(8, 1, "2026-01-01"))
        sheet.addCell(Label(9, 1, "一年"))

        workbook.write()
        workbook.close()
        Toast.makeText(context, "範本已儲存至 '下載' 資料夾", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "建立範本失敗: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/**
 * 【*** 新增 ***】
 * 建立電表資料 Excel 範本的輔助函式。
 */
private fun createElectricExcelTemplate(context: Context) {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "電表資料匯入範本.xls")
        val workbook: WritableWorkbook = Workbook.createWorkbook(file)
        val sheet = workbook.createSheet("電表資料", 0)
        val headers = listOf("房號", "月份", "度數")
        headers.forEachIndexed { index, header ->
            sheet.addCell(Label(index, 0, header))
        }
        // 新增範例資料
        sheet.addCell(Label(0, 1, "A101"))
        sheet.addCell(Label(1, 1, "2025-09"))
        sheet.addCell(Label(2, 1, "1500"))

        workbook.write()
        workbook.close()
        Toast.makeText(context, "範本已儲存至 '下載' 資料夾", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "建立範本失敗: ${e.message}", Toast.LENGTH_LONG).show()
    }
}