@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
// ContractPreviewScreen.kt
package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
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

@Composable
fun ContractPreviewScreen(navController: NavHostController) {
    var tenantName by remember { mutableStateOf("") }
    var landlordName by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var rentAmount by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            if (!showPreview) {
                Text("請填寫合約資訊", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("租客姓名") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = landlordName, onValueChange = { landlordName = it }, label = { Text("房東姓名") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = roomNumber, onValueChange = { roomNumber = it }, label = { Text("房號") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rentAmount, onValueChange = { rentAmount = it }, label = { Text("租金(元)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("租期起日 (YYYY/MM/DD)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("租期迄日 (YYYY/MM/DD)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        // 此處呼叫產生 PDF function
                        showPreview = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("預覽合約內容")
                }
            } else {
                Text("合約預覽", style = MaterialTheme.typography.headlineMedium)
                Text("租客：$tenantName\n房東：$landlordName\n房號：$roomNumber\n租金：$rentAmount 元\n租期：$startDate ~ $endDate")
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        // 這裡未來放產生PDF檔案與下載/分享功能
                        Toast.makeText(context, "尚未實作 PDF 生成功能", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("產生 PDF 電子合約")
                }
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
fun generateContractPdf(
    context: Context,
    tenantName: String,
    landlordName: String,
    roomNumber: String,
    rentAmount: String,
    startDate: String,
    endDate: String
) {
    val pdfDocument = PdfDocument()
    val paint = Paint()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4

    val page = pdfDocument.startPage(pageInfo)
    val canvas: Canvas = page.canvas

    // 合約內容
    var y = 60
    paint.textSize = 20f
    canvas.drawText("租賃電子合約", 220f, y.toFloat(), paint)
    paint.textSize = 16f
    y += 50
    canvas.drawText("租客姓名: $tenantName", 50f, y.toFloat(), paint)
    y += 30
    canvas.drawText("房東姓名: $landlordName", 50f, y.toFloat(), paint)
    y += 30
    canvas.drawText("房號: $roomNumber", 50f, y.toFloat(), paint)
    y += 30
    canvas.drawText("租金: $rentAmount 元", 50f, y.toFloat(), paint)
    y += 30
    canvas.drawText("租期: $startDate ~ $endDate", 50f, y.toFloat(), paint)
    y += 60
    paint.textSize = 14f
    canvas.drawText("簽約日期: ${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())}", 50f, y.toFloat(), paint)
    pdfDocument.finishPage(page)

    try {
        // 產生檔案名稱與路徑
        val fileName = "電子合約_${System.currentTimeMillis()}.pdf"
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, fileName)
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.close()
        Toast.makeText(context, "PDF 已儲存於 Download/$fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "PDF 產生失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
    pdfDocument.close()
}
