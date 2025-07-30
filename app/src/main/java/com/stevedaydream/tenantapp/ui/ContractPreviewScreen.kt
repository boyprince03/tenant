@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
// ContractPreviewScreen.kt
package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import com.stevedaydream.tenantapp.data.AppDatabase
import java.util.Calendar
import android.app.Activity

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts



@Composable
fun ContractPreviewScreen(navController: NavHostController) {
    var tenantName by remember { mutableStateOf("") }
    var tenantId by remember { mutableStateOf("") }
    var tenantPhone by remember { mutableStateOf("") }

    val landlordName = "王大明"
    val landlordId = "A123456789"
    val landlordPhone = "0912-345678"
    fun maskId(id: String): String {
        return if (id.length >= 5)
            id.substring(0, 2) + "*".repeat(id.length - 5) + id.takeLast(3)
        else "*".repeat(id.length)
    }

    fun maskPhone(phone: String): String {
        return if (phone.length >= 5)
            phone.substring(0, 2) + "*".repeat(phone.length - 5) + phone.takeLast(3)
        else "*".repeat(phone.length)
    }


    // 租期可選 0.5, 1, 2 年
    val periodOptions = listOf("0.5年", "1年", "2年")
    var periodIndex by remember { mutableStateOf(1) } // 預設一年

    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val roomDao = db.roomDao()
    val roomList by roomDao.getAllRooms().collectAsState(initial = emptyList())

    var expanded by remember { mutableStateOf(false) }
    var selectedRoom by remember { mutableStateOf("") }



    var address by remember { mutableStateOf("") }
    var rentAmount by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }

    var showPreview by remember { mutableStateOf(false) }


    val scrollState = rememberScrollState()
    // 日期處理
    val calendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    var startDate by remember { mutableStateOf(sdf.format(calendar.time)) }
    var endDate by remember { mutableStateOf("") }
    // 日曆選擇器彈窗

    fun showDatePicker(onDateSet: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = Calendar.getInstance()
                picked.set(y, m, d)
                onDateSet(sdf.format(picked.time))
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    // 每次 startDate 或 periodIndex 變動，自動計算 endDate
    LaunchedEffect(startDate, periodIndex) {
        val start = Calendar.getInstance()
        try {
            start.time = sdf.parse(startDate)!!
            val years = when (periodIndex) {
                0 -> 0 // 0.5年
                1 -> 1
                2 -> 2
                else -> 1
            }
            val months = if (periodIndex == 0) 6 else 0
            start.add(Calendar.YEAR, years)
            start.add(Calendar.MONTH, months)
            endDate = sdf.format(start.time)
        } catch (e: Exception) {
            endDate = ""
        }
    }


    val today = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
    val contractTemplate = """
        住宅租賃契約書
        房號：%s
        租賃地址：%s
        租賃期間：自 %s 起至 %s 止
        承租人：%s　　出租人：%s
        本契約依內政部109年8月26日台內地字第1090264511號函修正

        第一條　租賃標的
        1. 租賃住宅標示：門牌：%s
        2. 租賃範圍：房號：%s，附屬設備依附件。
        3. 車位：依契約內容約定。

        第二條　租賃期間
        自 %s 起至 %s 止。（租賃期間至少三十日以上）

        第三條　租金約定及支付
        承租人每月租金為新臺幣 %s 元整，每期應繳納 1 個月租金，並於每月 5 日前支付，不得藉任何理由拖延或拒絕。
        出租人於租賃期間亦不得藉任何理由要求調漲租金。
        租金支付方式：現金或轉帳，帳戶資訊詳契約正本。

        第四條　押金約定及返還
        押金由租賃雙方約定為 2 個月租金，金額為 %s 元整（最高不得超過二個月租金之總額）。承租人應於簽訂本契約之同時給付出租人。
        前項押金，除有第十一條第四項、第十三條第三項、第十四條第四項及第十八條第二項得抵充之情形外，出租人應於租期屆滿或租賃契約終止，承租人返還租賃住宅時，返還押金或抵充本契約所生債務後之賸餘押金。

        第五條　租賃期間相關費用之約定
        管理費：無
        水費：由出租人負擔。
        電費：由承租人負擔。（備註：公共區域電費由房東負擔）
        瓦斯費：無
        網路費：由出租人負擔。

        第六條　稅費負擔之約定
        本契約有關稅費，依下列約定辦理：
        租賃住宅之房屋稅、地價稅由出租人負擔。
        本契約租賃雙方不同意辦理公證。

        第七條　使用租賃住宅之限制
        1. 本租賃住宅係供居住使用，承租人不得變更用途。
        2. 承租人同意遵守公寓大廈規約或其他住戶應遵行事項，不得違法使用、存放有爆炸性或易燃性物品。
        3. 承租人應經出租人同意始得將本租賃住宅之全部或一部分轉租、出借或以其他方式供他人使用，或將租賃權轉讓於他人。前項出租人同意轉租者，應出具同意書載明同意轉租之範圍、期間及得終止本契約之事由，供承租人轉租時向次承租人提示。
        4. 未經同意不可養寵物，若違反條款屢勸不聽者，將依法多扣除至多1個月押金並限期搬離。

        第八條　修繕
        1. 租賃住宅或附屬設備損壞時，應由出租人負責修繕。但租賃雙方另有約定、習慣或其損壞係可歸責於承租人之事由者，不在此限。
        2. 前項由出租人負責修繕者，承租人得定相當期限催告修繕，如出租人未於承租人所定相當期限內修繕時，承租人得自行修繕，並請求出租人償還其費用或於第三條約定之租金中扣除。
        3. 出租人為修繕租賃住宅所為之必要行為，應於相當期間先期通知，承租人無正當理由不得拒絕。
        4. 前項出租人於修繕期間，致租賃住宅全部或一部不能居住使用者，承租人得請求出租人扣除該期間全部或一部之租金。

        ...（省略部分條款，完整請依你的HTML內容繼續補上）...

        簽約日期：%s

        立契約書人
        出租人：%s，身分證字號：%s，聯絡電話：%s
        承租人：%s，身分證字號：%s，聯絡電話：%s
        中華民國 %s
    """.trimIndent()

    // 4. 合約內容生成時
    val previewText = String.format(
        contractTemplate,
        selectedRoom, // 合約所有房號都用這個
        address,
        startDate,
        endDate,
        tenantName,
        landlordName,
        address,
        selectedRoom,
        startDate,
        endDate,
        rentAmount,
        deposit,
        today,
        landlordName, landlordId, landlordPhone,
        tenantName, tenantId, tenantPhone,
        today
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("電子合約預覽") },
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
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (!showPreview) {
                LaunchedEffect(roomList) {
                    if (selectedRoom.isEmpty() && roomList.isNotEmpty()) {
                        selectedRoom = roomList.first().roomNumber
                    }
                }

                Text("請填寫合約資訊", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("租客姓名") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tenantId, onValueChange = { tenantId = it }, label = { Text("租客身分證字號") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tenantPhone, onValueChange = { tenantPhone = it }, label = { Text("租客電話") }, modifier = Modifier.fillMaxWidth())
                // 房東欄位 disabled
                OutlinedTextField(value = landlordName, onValueChange = {}, label = { Text("房東姓名") }, modifier = Modifier.fillMaxWidth(), enabled = false)
                OutlinedTextField(
                    value = maskId(landlordId),
                    onValueChange = {},
                    label = { Text("房東身分證字號") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )
                OutlinedTextField(
                    value = maskPhone(landlordPhone),
                    onValueChange = {},
                    label = { Text("房東電話") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )

                // 3. 下拉選單
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedRoom,
                        onValueChange = {},
                        label = { Text("房號") },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .clickable { expanded = true }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        roomList.forEach { room ->
                            DropdownMenuItem(
                                text = { Text(room.roomNumber) },
                                onClick = {
                                    selectedRoom = room.roomNumber
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("租賃地址") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rentAmount, onValueChange = { rentAmount = it }, label = { Text("租金(元)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = deposit, onValueChange = { deposit = it }, label = { Text("押金(元)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                // 起租日：可點選日曆
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = {},
                        label = { Text("租期起日") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker { picked -> startDate = picked } }
                    )
                }



                // 租期選單
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("租期", Modifier.padding(end = 8.dp))
                    periodOptions.forEachIndexed { idx, label ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            RadioButton(selected = periodIndex == idx, onClick = { periodIndex = idx })
                            Text(label)
                        }
                    }
                }
                // 迄日自動產生不可改
                OutlinedTextField(
                    value = endDate,
                    onValueChange = {},
                    label = { Text("租期迄日") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { showPreview = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("預覽合約內容")
                }
            } else {
                Text("合約預覽", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                ) {
                    Text(previewText, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))

                // SAF 實現的 PDF 存檔按鈕
                PdfExportButton(context, previewText)

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showPreview = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("返回修改")
                }
            }
        }
    }
}

// PDF產生函數：產生純文字合約，內容與預覽一致
fun generateContractPdf(context: Context, contractText: String) {
    val pdfDocument = PdfDocument()
    val paint = Paint()
    paint.textSize = 14f
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4

    var page = pdfDocument.startPage(pageInfo)
    var canvas: Canvas = page.canvas

    val lines = contractText.split("\n")
    var y = 50
    val lineHeight = 22

    for (line in lines) {
        if (y > 800) { // 換頁
            try { pdfDocument.finishPage(page) } catch (_: Exception) {}
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 50
        }
        canvas.drawText(line, 40f, y.toFloat(), paint)
        y += lineHeight
    }
    try { pdfDocument.finishPage(page) } catch (_: Exception) {}

    try {
        // 只用 App 的外部私有空間，不會有權限問題
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir != null && !dir.exists()) dir.mkdirs()
        val fileName = "電子合約_${System.currentTimeMillis()}.pdf"
        val file = File(dir, fileName)
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.close()
        Toast.makeText(context, "PDF 已儲存於 ${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "PDF 產生失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
    try { pdfDocument.close() } catch (_: Exception) {}
}
@Composable
fun PdfExportButton(context: Context, contractText: String) {
    // 保存檔案時會呼叫的 callback
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri: Uri? ->
            if (uri != null) {
                savePdfWithUri(context, uri, contractText)
            }
        }
    )

    Button(onClick = {
        val fileName = "電子合約_${System.currentTimeMillis()}.pdf"
        savePdfLauncher.launch(fileName)
    }) {
        Text("產生 PDF 電子合約")
    }
}
fun savePdfWithUri(context: Context, uri: Uri, contractText: String) {
    val pdfDocument = PdfDocument()
    val paint = Paint()
    paint.textSize = 14f
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()

    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas
    val lines = contractText.split("\n")
    var y = 50
    val lineHeight = 22

    for (line in lines) {
        if (y > 800) {
            try { pdfDocument.finishPage(page) } catch (_: Exception) {}
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 50
        }
        canvas.drawText(line, 40f, y.toFloat(), paint)
        y += lineHeight
    }
    try { pdfDocument.finishPage(page) } catch (_: Exception) {}

    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        Toast.makeText(context, "PDF 已儲存", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "PDF 產生失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
    try { pdfDocument.close() } catch (_: Exception) {}
}

