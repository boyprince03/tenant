// app/src/main/java/com/stevedaydream/tenantapp/ui/ElectricityCalcScreen.kt
package com.stevedaydream.tenantapp.ui

import ElectricityCalcViewModel
import android.app.DatePickerDialog
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.ElectricMeterDao
import com.stevedaydream.tenantapp.data.RoomDao
import java.text.SimpleDateFormat
import java.util.*


// ViewModel Factory 用於提供帶有依賴的 ViewModel
class ElectricityCalcViewModelFactory(
    private val roomDao: RoomDao,
    private val meterDao: ElectricMeterDao
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ElectricityCalcViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ElectricityCalcViewModel(roomDao, meterDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityCalcScreen(
    roomDao: RoomDao,
    meterDao: ElectricMeterDao,
    navController: NavHostController,
    onNavigateToQuery: () -> Unit,
    viewModel: ElectricityCalcViewModel = viewModel(
        factory = ElectricityCalcViewModelFactory(roomDao, meterDao)
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // --- 月曆 Dialog 控制 ---
    if (uiState.showMonthPicker) {
        val cal = Calendar.getInstance()
        try {
            val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            cal.time = monthFormatter.parse(uiState.currentMonth) ?: Date()
        } catch (_: Exception) {}
        DatePickerDialog(
            context,
            { _, y, m, _ ->
                viewModel.onMonthSelected(y, m)
                viewModel.onDismissMonthPicker()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            1
        ).apply {
            datePicker.findViewById<View>(
                context.resources.getIdentifier("android:id/day", null, null)
            )?.visibility = View.GONE
            setOnCancelListener { viewModel.onDismissMonthPicker() }
        }.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("電表計算頁面", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 新增：上個月按鈕
                    IconButton(onClick = { viewModel.onPreviousMonth() }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上個月")
                    }
                    Text(
                        text = uiState.currentMonth, // 僅顯示月份，移除前綴文字
                        modifier = Modifier
                            .padding(horizontal = 8.dp) // 調整間距
                            .clickable { viewModel.onShowMonthPicker() }, // 新增點擊事件，點擊文字也能開啟日曆
                        style = MaterialTheme.typography.bodyLarge
                    )
                    // 新增：下個月按鈕
                    IconButton(onClick = { viewModel.onNextMonth() }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下個月")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 房間輸入區塊
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("輸入各房號本月度數", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (uiState.roomList.isEmpty()) {
                        Text("請先新增房間資料", color = MaterialTheme.colorScheme.error)
                    } else {
                        RoomMeterInputList(
                            roomList = uiState.roomList,
                            meterMap = uiState.meterMap,
                            lockedRoomMap = uiState.lockedRoomMap,
                            onValueChange = viewModel::onMeterValueChange,
                            onLockToggle = viewModel::onLockToggle,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ElevatedButton(
                        onClick = { viewModel.saveAndCalculate() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.canSave
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "儲存", modifier = Modifier.padding(end = 8.dp))
                        Text("儲存並計算")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // 預覽區
            if (uiState.usedMap.isNotEmpty()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("各房間本月用電與費用：", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        uiState.usedMap.forEach { (roomNo, used) ->
                            val fee = uiState.feeMap[roomNo] ?: 0.0f
                            Text("$roomNo 房：$used 度 / 約 $fee 元", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                Text(
                    "尚未輸入或計算本月電費資料。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToQuery,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("前往電費查詢頁面")
            }

            // 訊息提示
            if (uiState.message.isNotEmpty()) {
                Text(
                    text = uiState.message,
                    color = when (uiState.messageType) {
                        ElectricityCalcViewModel.MessageType.Success -> MaterialTheme.colorScheme.primary
                        ElectricityCalcViewModel.MessageType.Error -> MaterialTheme.colorScheme.error
                        ElectricityCalcViewModel.MessageType.Info -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}


@Composable
fun RoomMeterInputList(
    roomList: List<com.stevedaydream.tenantapp.data.RoomEntity>,
    meterMap: Map<String, String>,
    lockedRoomMap: Map<String, Boolean>,
    onValueChange: (String, String) -> Unit,
    onLockToggle: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(roomList, key = { it.roomNumber }) { room ->
            val locked = lockedRoomMap[room.roomNumber] == true
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = meterMap[room.roomNumber] ?: "",
                    onValueChange = { newValue -> onValueChange(room.roomNumber, newValue) },
                    label = { Text("房號 ${room.roomNumber} 度數") },
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (locked) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            else Modifier
                        ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !locked,
                    singleLine = true,
                    isError = meterMap[room.roomNumber]?.toIntOrNull() == null &&
                            !meterMap[room.roomNumber].isNullOrBlank()
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onLockToggle(room.roomNumber) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(if (locked) "解鎖" else "鎖定")
                }
            }
        }
    }
}

// 移除 MonthPickerField，因為功能已整合到 TopAppBar