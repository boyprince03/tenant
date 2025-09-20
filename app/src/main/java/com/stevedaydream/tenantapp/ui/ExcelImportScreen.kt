package com.stevedaydream.tenantapp.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.ElectricMeterDao
import com.stevedaydream.tenantapp.data.ElectricMeterRecord
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.RoomEntity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import jxl.Workbook
import jxl.write.Label
import jxl.write.WritableWorkbook
import java.io.File
import java.io.InputStream
import android.os.Environment
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Input
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor

// Helper function: Show a Toast message
private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** * 【核心修改】Create a room data Excel template
 * - 新增「房屋狀態」和「租賃期間」欄位
 */
fun createRoomExcelTemplate(context: Context): String? {
    return try {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, "房間資料範本.xls")
        val workbook: WritableWorkbook = Workbook.createWorkbook(file)
        val sheet = workbook.createSheet("Sheet1", 0)

        // 更新 Headers
        val headers = listOf("房號", "租客姓名", "房型", "租金", "押金", "房屋狀態", "起租日", "結束日", "租賃期間", "備註")
        headers.forEachIndexed { i, header ->
            sheet.addCell(Label(i, 0, header))
        }
        // 更新 Demo Data
        val demo = listOf(
            listOf("401", "張三", "雅房", "6000", "12000", "出租中", "2024-07-01", "2025-06-30", "1年", ""),
            listOf("402", "李四", "套房", "8500", "17000", "出租中", "2024-08-01", "2025-07-31", "1年", "頂樓加蓋"),
            listOf("501", "", "雅房", "5500", "11000", "可租", "", "", "", "")
        )
        demo.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell ->
                sheet.addCell(Label(c, r + 1, cell))
            }
        }
        workbook.write()
        workbook.close()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** Create an electric meter reading Excel template (此部分不需修改) */
fun createElectricExcelTemplate(context: Context): String? {
    return try {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, "電表度數範本.xls")
        val workbook: WritableWorkbook = Workbook.createWorkbook(file)
        val sheet = workbook.createSheet("Sheet1", 0)

        val headers = listOf("房號", "月份", "度數")
        headers.forEachIndexed { i, header ->
            sheet.addCell(Label(i, 0, header))
        }
        val demo = listOf(
            listOf("401", "2024-07", "126"),
            listOf("402", "2024-07", "98")
        )
        demo.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell ->
                sheet.addCell(Label(c, r + 1, cell))
            }
        }
        workbook.write()
        workbook.close()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelImportScreen(
    roomDao: RoomDao,
    meterDao: ElectricMeterDao,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewRows by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var detectedType by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    suspend fun parseExcelAndDetectType(uri: Uri): Pair<List<Map<String, String>>, String?>? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val workbook = Workbook.getWorkbook(inputStream)
            val sheet = workbook.getSheet(0)
            val headers = (0 until sheet.columns).map { sheet.getCell(it, 0).contents.trim() }

            // 【核心修改】更新房間資料的 Headers
            val roomHeaders = listOf("房號", "租客姓名", "房型", "租金", "押金", "房屋狀態", "起租日", "結束日", "租賃期間", "備註")
            val meterHeaders = listOf("房號", "月份", "度數")

            val detectedType = when {
                // 使用 containsAll 確保所有必要欄位都存在，允許 Excel 有多餘欄位
                headers.containsAll(listOf("房號", "租客姓名", "房型", "租金")) -> "房間"
                headers.containsAll(meterHeaders) -> "電表"
                else -> null
            }

            if (detectedType == null) {
                workbook.close()
                return null
            }

            val existingKeys = when (detectedType) {
                "房間" -> roomDao.getAll().firstOrNull()?.map { it.roomNumber }?.toSet() ?: emptySet()
                "電表" -> meterDao.getAll().firstOrNull()?.map { "${it.roomNumber}-${it.recordMonth}" }?.toSet() ?: emptySet()
                else -> emptySet()
            }

            val result = mutableListOf<Map<String, String>>()
            for (row in 1 until sheet.rows) {
                val map = mutableMapOf<String, String>()
                for (col in headers.indices) {
                    map[headers[col]] = sheet.getCell(col, row).contents.trim()
                }

                val isDuplicate = when (detectedType) {
                    "房間" -> map["房號"] in existingKeys
                    "電表" -> "${map["房號"]}-${map["月份"]}" in existingKeys
                    else -> false
                }

                if (isDuplicate) {
                    map["重複"] = "是" // Add a flag for duplicates
                }

                result.add(map)
            }
            workbook.close()
            return Pair(result, detectedType)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            inputStream?.close()
        }
    }


    // 1. Open file chooser
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isLoading = true
            scope.launch {
                val result = parseExcelAndDetectType(uri)
                if (result != null && result.second != null) {
                    previewRows = result.first
                    detectedType = result.second
                    message = if (previewRows.isEmpty()) "預覽失敗，請檢查檔案格式或內容" else "預覽成功，已自動偵測為【${detectedType}】資料"
                } else {
                    message = "檔案格式不正確或無法辨識，請下載範本確認。"
                    previewRows = emptyList()
                    detectedType = null
                }
                isLoading = false
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Excel 資料匯入", fontWeight = FontWeight.Bold) },
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
                Text(
                    "請先下載對應的 Excel 範本檔案，並根據範本格式填寫您的資料。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ElevatedButton(
                        onClick = {
                            val path = createRoomExcelTemplate(context)
                            showToast(context, if (path != null) "已下載至: $path" else "下載失敗")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("房間資料")
                    }
                    ElevatedButton(
                        onClick = {
                            val path = createElectricExcelTemplate(context)
                            showToast(context, if (path != null) "已下載至: $path" else "下載失敗")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("電表度數")
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
                    onClick = { launcher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DriveFolderUpload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("選擇 Excel 檔案")
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                }

                if (message.isNotEmpty()) {
                    Text(
                        message,
                        color = if (message.startsWith("檔案格式")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // 步驟三：確認與匯入
            if (previewRows.isNotEmpty()) {
                val nonDuplicateCount = previewRows.count { it["重複"] != "是" }
                StepCard(
                    step = "3",
                    title = "確認與匯入",
                    icon = Icons.Default.CheckCircle
                ) {
                    Text("預覽資料（前5筆）：", style = MaterialTheme.typography.titleSmall)
                    previewRows.take(5).forEachIndexed { idx, row ->
                        val isDuplicate = row["重複"] == "是"
                        val displayText = "${idx + 1}. " + when(detectedType) {
                            "房間" -> "房號=${row["房號"]}, 租客=${row["租客姓名"]}"
                            "電表" -> "房號=${row["房號"]}, 月份=${row["月份"]}, 度數=${row["度數"]}"
                            else -> ""
                        }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDuplicate) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        "共 ${previewRows.size} 筆資料，其中 $nonDuplicateCount 筆可匯入。",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "紅色標示為資料庫已存在，匯入時將自動跳過。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val result = importExcelToDb(previewRows, detectedType!!, roomDao, meterDao)
                                showToast(context, result)
                                if (result.startsWith("成功")) {
                                    previewRows = emptyList()
                                    detectedType = null
                                    message = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = nonDuplicateCount > 0
                    ) {
                        Icon(Icons.Default.Input, contentDescription = "匯入", modifier = Modifier.padding(end = 8.dp))
                        Text("開始匯入")
                    }
                }
            }
        }
    }
}
@Composable
private fun StepCard(
    step: String,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                Text("步驟 $step: $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            content()
        }
    }
}

/** * 【核心修改】Import data to DB, checking for duplicates
 * - 新增 status 和 rentDuration 欄位
 */
suspend fun importExcelToDb(
    data: List<Map<String, String>>,
    type: String,
    roomDao: RoomDao,
    meterDao: ElectricMeterDao
): String {
    val nonDuplicateData = data.filter { it["重複"] != "是" }

    return when (type) {
        "房間" -> {
            val newRooms = nonDuplicateData.mapNotNull { row ->
                val roomNo = row["房號"] ?: return@mapNotNull null
                RoomEntity(
                    roomNumber = roomNo,
                    tenantName = row["租客姓名"] ?: "",
                    type = row["房型"] ?: "",
                    note = row["備註"] ?: "",
                    rentAmount = row["租金"]?.toIntOrNull() ?: 0,
                    deposit = row["押金"]?.toIntOrNull() ?: 0,
                    status = row["房屋狀態"] ?: "可租", // 新增
                    rentStartDate = row["起租日"] ?: "",
                    rentEndDate = row["結束日"] ?: "",
                    rentDuration = row["租賃期間"] ?: "", // 新增
                    landlordCode = "" // 保持原有邏輯，landlordCode 之後再綁定
                )
            }
            if (newRooms.isNotEmpty()) {
                roomDao.insertRooms(newRooms)
            }
            val skippedCount = data.size - newRooms.size
            "成功匯入 ${newRooms.size} 筆房間資料。已跳過 $skippedCount 筆重複資料。"
        }
        "電表" -> {
            val newRecords = nonDuplicateData.mapNotNull { row ->
                val roomNo = row["房號"] ?: return@mapNotNull null
                val month = row["月份"] ?: return@mapNotNull null
                val value = row["度數"]?.toIntOrNull() ?: return@mapNotNull null
                ElectricMeterRecord(roomNumber = roomNo, recordMonth = month, meterValue = value)
            }
            if (newRecords.isNotEmpty()) {
                meterDao.insertOrUpdateRecords(newRecords)
            }
            val skippedCount = data.size - newRecords.size
            "成功匯入 ${newRecords.size} 筆電表資料。已跳過 $skippedCount 筆重複資料。"
        }
        else -> "型態錯誤"
    }
}