package com.stevedaydream.tenantapp.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.ElectricMeterDao
import com.stevedaydream.tenantapp.data.ElectricMeterRecord
import kotlinx.coroutines.launch
import jxl.Workbook
import android.os.Environment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import jxl.write.Label
import jxl.write.WritableWorkbook
import java.io.File

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
                sheet.addCell(Label(c, r+1, cell))
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
                sheet.addCell(Label(c, r+1, cell))
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
    var importType by remember { mutableStateOf("房間") } // "房間" 或 "電表"

    // 1. 打開檔案選擇器
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            excelUri = uri
            val data = parseExcelPreview(context, uri)
            previewRows = data
            message = if (data.isEmpty()) "預覽失敗，請檢查檔案格式" else "預覽成功"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Excel 資料匯入") },
            actions = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        ) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            // 匯入型態選擇
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("選擇匯入資料型態：")
                RadioButton(selected = importType == "房間", onClick = { importType = "房間" })
                Text("房間", modifier = Modifier.padding(end = 12.dp))
                RadioButton(selected = importType == "電表", onClick = { importType = "電表" })
                Text("電表")
            }

            // 選擇 Excel 按鈕
            Button(
                onClick = { launcher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("選擇 Excel 檔案")
            }
            Spacer(Modifier.height(16.dp))

            // Excel 資料預覽
            if (previewRows.isNotEmpty()) {
                Text("預覽資料（前5筆）：", style = MaterialTheme.typography.titleMedium)
                previewRows.take(5).forEachIndexed { idx, row ->
                    Text("$idx. ${row}")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val result = importExcelToDb(context, previewRows, importType, roomDao, meterDao)
                            message = result
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("匯入資料到本地資料庫") }
            }
            Spacer(Modifier.height(12.dp))
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(32.dp))
            Text(
                "【Excel 格式說明】\n" +
                        "★ 房間資訊需欄位：房號、租客姓名、房型、租金、押金、起租日、結束日、備註（欄位名稱可多但這些必須有）\n" +
                        "★ 電表度數需欄位：房號、月份、度數\n"+
                        "★ 若不確定格式，請下載範本填寫後再進行匯入 ↓"
            )
            Spacer(Modifier.height(8.dp))
            // 在 UI 的適當位置加入
            Text("下載 Excel 範本：", style = MaterialTheme.typography.titleMedium)
            Row {
                Button(onClick = {
                    val path = createRoomExcelTemplate(context)
                    Toast.makeText(context, if (path != null) "已下載至: $path" else "下載失敗", Toast.LENGTH_SHORT).show()
                }) { Text("房間資料範本") }
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    val path = createElectricExcelTemplate(context)
                    Toast.makeText(context, if (path != null) "已下載至: $path" else "下載失敗", Toast.LENGTH_SHORT).show()
                }) { Text("電表度數範本") }
            }

//            Spacer(Modifier.height(8.dp))
//            Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
//                Text("返回")
//            }
        }
    }
}

/** 解析Excel為List<Map>，欄位名以第一列為key */
fun parseExcelPreview(context: Context, uri: Uri): List<Map<String, String>> {
    val result = mutableListOf<Map<String, String>>()
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        val workbook = Workbook.getWorkbook(inputStream)
        val sheet = workbook.getSheet(0)
        val headers = (0 until sheet.columns).map { sheet.getCell(it, 0).contents.trim() }
        for (row in 1 until sheet.rows) {
            val map = mutableMapOf<String, String>()
            for (col in headers.indices) {
                map[headers[col]] = sheet.getCell(col, row).contents.trim()
            }
            result.add(map)
        }
        workbook.close()
        inputStream.close()
    } catch (e: Exception) {
        e.printStackTrace()
        return emptyList()
    }
    return result
}

/** 寫入DB：自動判斷欄位對應，批量寫入 */
suspend fun importExcelToDb(
    context: Context,
    data: List<Map<String, String>>,
    type: String,
    roomDao: RoomDao,
    meterDao: ElectricMeterDao
): String {
    return when (type) {
        "房間" -> {
            val rooms = data.mapNotNull { row ->
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
                    rentDuration = "" // 可擴充
                )
            }
            roomDao.insertRooms(rooms)
            "成功匯入 ${rooms.size} 筆房間資料"
        }
        "電表" -> {
            val records = data.mapNotNull { row ->
                val roomNo = row["房號"] ?: return@mapNotNull null
                val month = row["月份"] ?: return@mapNotNull null
                val value = row["度數"]?.toIntOrNull() ?: return@mapNotNull null
                ElectricMeterRecord(roomNumber = roomNo, recordMonth = month, meterValue = value)
            }
            meterDao.insertOrUpdateRecords(records)
            "成功匯入 ${records.size} 筆電表資料"
        }
        else -> "型態錯誤"
    }
}
