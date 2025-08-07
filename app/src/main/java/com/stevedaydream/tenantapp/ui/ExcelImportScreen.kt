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

// Helper function: Show a Toast message
private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** Create a room data Excel template */
fun createRoomExcelTemplate(context: Context): String? {
    return try {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, "房間資料範本.xls")
        val workbook: WritableWorkbook = Workbook.createWorkbook(file)
        val sheet = workbook.createSheet("Sheet1", 0)

        val headers = listOf("房號", "租客姓名", "房型", "租金", "押金", "起租日", "結束日", "備註")
        headers.forEachIndexed { i, header ->
            sheet.addCell(Label(i, 0, header))
        }
        val demo = listOf(
            listOf("401", "張三", "雅房", "6000", "12000", "2024-07-01", "2025-06-30", ""),
            listOf("402", "李四", "套房", "8500", "17000", "2024-08-01", "2025-07-31", "頂樓加蓋")
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

/** Create an electric meter reading Excel template */
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
    var excelUri by remember { mutableStateOf<Uri?>(null) }
    var previewRows by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var importType by remember { mutableStateOf("房間") } // "房間" or "電表"

    // Helper function to parse Excel, auto-detect type, and check for duplicates
    suspend fun parseExcelAndDetectType(uri: Uri): Pair<List<Map<String, String>>, String?>? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val workbook = Workbook.getWorkbook(inputStream)
            val sheet = workbook.getSheet(0)
            val headers = (0 until sheet.columns).map { sheet.getCell(it, 0).contents.trim() }

            // Check if headers match Room or Electric Meter format
            val roomHeaders = listOf("房號", "租客姓名", "房型", "租金", "押金", "起租日", "結束日", "備註")
            val meterHeaders = listOf("房號", "月份", "度數")

            val detectedType = when {
                headers.containsAll(roomHeaders) -> "房間"
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
            excelUri = uri
            scope.launch {
                val result = parseExcelAndDetectType(uri)
                if (result != null) {
                    previewRows = result.first
                    importType = result.second ?: "房間" // Default to 房間 if type is null
                    message = if (previewRows.isEmpty()) "預覽失敗，請檢查檔案格式或內容" else "預覽成功，已自動偵測為${importType}資料"
                } else {
                    message = "檔案格式不正確，請選擇房間或電表度數的範本檔案。"
                    previewRows = emptyList()
                    excelUri = null
                }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Import type selection
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("選擇匯入資料型態 (已自動偵測)", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = importType == "房間", onClick = {
                                importType = "房間"
                                previewRows = emptyList()
                                message = ""
                            })
                            Text("房間資料")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = importType == "電表", onClick = {
                                importType = "電表"
                                previewRows = emptyList()
                                message = ""
                            })
                            Text("電表度數")
                        }
                    }
                }
            }

            // Select Excel file button
            ElevatedButton(
                onClick = { launcher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DriveFolderUpload, contentDescription = "選擇檔案", modifier = Modifier.padding(end = 8.dp))
                Text("選擇 Excel 檔案進行預覽", style = MaterialTheme.typography.bodyLarge)
            }

            // Excel data preview and import
            if (previewRows.isNotEmpty()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("預覽資料（前5筆）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "• 紅色標示為資料庫已存在，匯入時將跳過。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        previewRows.take(5).forEachIndexed { idx, row ->
                            val isDuplicate = row["重複"] == "是"
                            val displayText = "${idx + 1}. ${row.filterKeys { it != "重複" }}"
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDuplicate) MaterialTheme.colorScheme.error else Color.Unspecified
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        ElevatedButton(
                            onClick = {
                                scope.launch {
                                    val result = importExcelToDb(previewRows, importType, roomDao, meterDao)
                                    message = result
                                    // Clear preview after successful import
                                    if (result.startsWith("成功")) {
                                        previewRows = emptyList()
                                        excelUri = null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "匯入", modifier = Modifier.padding(end = 8.dp))
                            Text("匯入資料到本地資料庫", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (message.isNotEmpty()) {
                Text(message, color = if (message.startsWith("檔案格式")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            // Template download section
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = "說明", modifier = Modifier.padding(end = 8.dp))
                        Text("Excel 格式說明", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "• 房間資料需欄位：房號、租客姓名、房型、租金、押金、起租日、結束日、備註\n" +
                                "• 電表度數需欄位：房號、月份、度數",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("下載範本填寫後再匯入：", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
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
                            Icon(Icons.Default.Description, contentDescription = "房間範本", modifier = Modifier.padding(end = 8.dp))
                            Text("房間資料")
                        }
                        ElevatedButton(
                            onClick = {
                                val path = createElectricExcelTemplate(context)
                                showToast(context, if (path != null) "已下載至: $path" else "下載失敗")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.List, contentDescription = "電表範本", modifier = Modifier.padding(end = 8.dp))
                            Text("電表度數")
                        }
                    }
                }
            }
        }
    }
}

/** Import data to DB, checking for duplicates */
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
                    status = "",
                    rentStartDate = row["起租日"] ?: "",
                    rentEndDate = row["結束日"] ?: "",
                    landlordCode = "",
                    rentDuration = ""
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
