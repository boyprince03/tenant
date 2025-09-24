package com.stevedaydream.tenantapp.ui.shared

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.User
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomEditDialog(
    room: RoomEntity,
    isNew: Boolean,
    allLandlords: List<User>, // 傳入所有房東列表以建立下拉選單
    onDismiss: () -> Unit,
    onSave: (RoomEntity, List<Uri>) -> Unit,
    onDelete: (RoomEntity) -> Unit
) {
    var roomNumber by remember { mutableStateOf(room.roomNumber) }
    var tenantName by remember { mutableStateOf(room.tenantName) }
    var type by remember { mutableStateOf(room.type) }
    var note by remember { mutableStateOf(room.note) }
    var rentAmount by remember { mutableStateOf(if (room.rentAmount == 0) "" else room.rentAmount.toString()) }
    var deposit by remember { mutableStateOf(if (room.deposit == 0) "" else room.deposit.toString()) }
    var status by remember { mutableStateOf(room.status.ifBlank { "可租" }) }
    val statusOptions = listOf("可租", "出租中", "維修中")
    var rentEndDate by remember { mutableStateOf(room.rentEndDate) }
    var rentDuration by remember { mutableStateOf(room.rentDuration) }
    val durationOptions = listOf("半年", "一年", "二年")
    var rentDurationIndex by remember { mutableStateOf(durationOptions.indexOf(room.rentDuration).coerceAtLeast(1)) }
    var rentStartDate by remember { mutableStateOf(room.rentStartDate) }
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val context = LocalContext.current

    // 【*** 新增 1：管理圖片 URL 的狀態 ***】
    // imageUrls 用於存放已上傳的網路圖片 URL
    var imageUrls by remember { mutableStateOf(room.imageUrls) }
    // newImageUris 用於存放使用者剛從手機選取，還未上傳的圖片 Uri
    var newImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // 【*** 新增 2：圖片選取器 ***】
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        // 限制總圖片數量不超過 5 張
        val currentCount = imageUrls.size + newImageUris.size
        val canAddCount = 5 - currentCount
        if (canAddCount > 0) {
            newImageUris = newImageUris + uris.take(canAddCount)
        }
    }

    // 用於指派房東的狀態
    var selectedLandlordCode by remember { mutableStateOf(room.landlordCode) }
    var landlordDropdownExpanded by remember { mutableStateOf(false) }
    val selectedLandlord = allLandlords.find { it.landlordCode == selectedLandlordCode }

    fun showDatePicker(onDateSet: (String) -> Unit) {
        val c = Calendar.getInstance()
        if (rentStartDate.isNotBlank()) {
            try { c.time = sdf.parse(rentStartDate)!! } catch (_: Exception) {}
        }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = Calendar.getInstance(); picked.set(y, m, d)
                onDateSet(sdf.format(picked.time))
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    LaunchedEffect(rentStartDate, rentDurationIndex) {
        if (rentStartDate.isNotBlank()) {
            try {
                val start = Calendar.getInstance(); start.time = sdf.parse(rentStartDate)!!
                val years = when (rentDurationIndex) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 1 }
                val months = if (rentDurationIndex == 0) 6 else 0
                start.add(Calendar.YEAR, years); start.add(Calendar.MONTH, months); start.add(Calendar.DAY_OF_MONTH, -1)
                rentEndDate = sdf.format(start.time); rentDuration = durationOptions[rentDurationIndex]
            } catch (e: Exception) { rentEndDate = "" }
        } else { rentEndDate = "" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新增房間" else "編輯房間 - ${room.roomNumber}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(value = roomNumber, onValueChange = { roomNumber = it }, label = { Text("房號") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("房型 (例：雅房、套房)") }, modifier = Modifier.fillMaxWidth())

                // 房東指派下拉選單
                ExposedDropdownMenuBox(
                    expanded = landlordDropdownExpanded,
                    onExpandedChange = { landlordDropdownExpanded = !it },
                ) {
                    OutlinedTextField(
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        value = selectedLandlord?.username ?: "未指派",
                        onValueChange = {},
                        label = { Text("指派房東") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = landlordDropdownExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = landlordDropdownExpanded,
                        onDismissRequest = { landlordDropdownExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("未指派") },
                            onClick = {
                                selectedLandlordCode = null
                                landlordDropdownExpanded = false
                            }
                        )
                        allLandlords.forEach { landlord ->
                            DropdownMenuItem(
                                text = { Text("${landlord.username} (${landlord.landlordCode})") },
                                onClick = {
                                    selectedLandlordCode = landlord.landlordCode
                                    landlordDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("租客姓名 (可留空)") }, modifier = Modifier.fillMaxWidth())
                Divider(Modifier.padding(vertical = 4.dp))
                Text("房屋狀態", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    statusOptions.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { status = option }.padding(end = 8.dp)) {
                            RadioButton(selected = (status == option), onClick = { status = option })
                            Text(text = option)
                        }
                    }
                }
                OutlinedTextField(
                    value = rentAmount,
                    onValueChange = {
                        rentAmount = it.filter { c -> c.isDigit() }
                        val amount = rentAmount.toIntOrNull() ?: 0
                        deposit = if (amount > 0) (amount * 2).toString() else ""
                    },
                    label = { Text("月租金(元)") }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = deposit, onValueChange = {}, label = { Text("押金(自動計算為2個月)") }, readOnly = true, enabled = false, modifier = Modifier.fillMaxWidth())
                Divider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("租期", Modifier.padding(end = 8.dp))
                    durationOptions.forEachIndexed { idx, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = rentDurationIndex == idx, onClick = { rentDurationIndex = idx })
                            Text(label)
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = rentStartDate, onValueChange = {}, label = { Text("租賃開始日") }, modifier = Modifier.fillMaxWidth(), readOnly = true)
                    Box(modifier = Modifier.matchParentSize().clickable { showDatePicker { picked -> rentStartDate = picked } })
                }
                OutlinedTextField(value = rentEndDate, onValueChange = {}, label = { Text("租賃結束日 (自動計算)") }, modifier = Modifier.fillMaxWidth(), enabled = false)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("備註") }, modifier = Modifier.fillMaxWidth())
                // 【*** 新增 3：圖片預覽與管理區塊 ***】
                Divider(Modifier.padding(vertical = 8.dp))
                Text("房間圖片 (最多5張)", style = MaterialTheme.typography.titleMedium)

                // 使用 LazyRow 橫向展示圖片
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 顯示已上傳的圖片 (URL)
                    items(imageUrls) { url ->
                        ImagePreviewItem(
                            data = url,
                            onRemove = { imageUrls = imageUrls - url }
                        )
                    }
                    // 顯示新選取的圖片 (Uri)
                    items(newImageUris) { uri ->
                        ImagePreviewItem(
                            data = uri,
                            onRemove = { newImageUris = newImageUris - uri }
                        )
                    }
                }

                // 如果圖片總數小於 5，則顯示 "新增圖片" 按鈕
                if (imageUrls.size + newImageUris.size < 5) {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "新增圖片", modifier = Modifier.padding(end = 8.dp))
                        Text("新增圖片")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (roomNumber.isNotBlank()) {
                    onSave(
                        room.copy(
                            roomNumber = roomNumber,
                            tenantName = tenantName,
                            type = type,
                            note = note,
                            rentAmount = rentAmount.toIntOrNull() ?: 0,
                            deposit = deposit.toIntOrNull() ?: 0,
                            status = status,
                            rentStartDate = rentStartDate,
                            rentEndDate = rentEndDate,
                            rentDuration = rentDuration,
                            landlordCode = selectedLandlordCode, // 儲存選擇的房東
                            imageUrls = imageUrls // 將目前的 URL 列表存入
                        ),
                        newImageUris // 將新選擇的圖片 Uri 列表也傳出去
                    )
                }
            }) { Text("儲存") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isNew) {
                    Button(
                        onClick = { onDelete(room) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("刪除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
// 【*** 新增 4：一個共用的圖片預覽 Composable ***】
@Composable
private fun ImagePreviewItem(data: Any, onRemove: () -> Unit) {
    Box(
        modifier = Modifier.size(100.dp)
    ) {
        // 使用 Coil 的 AsyncImage 來載入圖片，它能同時處理網路 URL 和本地 Uri
        AsyncImage(
            model = data,
            contentDescription = "房間圖片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // 右上角的刪除按鈕
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "移除圖片", tint = Color.White)
        }
    }
}