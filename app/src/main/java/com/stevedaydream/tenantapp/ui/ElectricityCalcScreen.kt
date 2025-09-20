// app/src/main/java/com/stevedaydream/tenantapp/ui/ElectricityCalcScreen.kt
package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityCalcScreen(
    roomDao: RoomDao,
    meterDao: ElectricMeterDao,
    navController: NavHostController,
    onNavigateToQuery: (Int) -> Unit,
    userRole: String
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    val viewModel: ElectricityCalcViewModel = viewModel(
        factory = ElectricityCalcViewModelFactory(roomDao, meterDao, userRole, settingsManager)
    )
    val uiState by viewModel.uiState.collectAsState()

    // 監聽 message 變化並顯示 Toast
    LaunchedEffect(uiState.message) {
        if (uiState.message.isNotBlank()) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_SHORT).show()
        }
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
                    IconButton(onClick = { viewModel.onPreviousMonth() }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上個月")
                    }
                    Text(
                        text = uiState.currentMonth,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { viewModel.onShowMonthPicker() },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(onClick = { viewModel.onNextMonth() }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下個月")
                    }
                    IconButton(onClick = { viewModel.onShowSettingsDialog() }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "計費公式設定")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.isEditEnabled && uiState.canSave) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.saveAndCalculate() },
                    icon = { Icon(Icons.Default.Save, contentDescription = "儲存") },
                    text = { Text("儲存並計算") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RoomMeterInputList(
                uiState = uiState,
                onMeterValueChange = viewModel::onMeterValueChange
            )
        }
    }

    if (uiState.showSettingsDialog) {
        uiState.settings?.let {
            CalculationSettingsDialog(
                currentSettings = it,
                onDismiss = viewModel::onDismissSettingsDialog,
                onSave = { newSettings ->
                    viewModel.saveSettings(newSettings)
                }
            )
        }
    }
}

// 房間度數輸入列表
@Composable
fun RoomMeterInputList(
    uiState: ElectricityCalcViewModel.UiState,
    onMeterValueChange: (String, String) -> Unit,
) {
    if (uiState.roomList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("沒有房間資料", style = MaterialTheme.typography.headlineSmall)
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(uiState.roomList) { room ->
                val isLocked = uiState.lockedRoomMap[room.roomNumber] ?: false
                val meterValue = uiState.meterMap[room.roomNumber] ?: ""
                val usage = uiState.usedMap[room.roomNumber]
                val fee = uiState.feeMap[room.roomNumber]

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "房號: ${room.roomNumber}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = meterValue,
                            onValueChange = { onMeterValueChange(room.roomNumber, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("本期電表度數") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = uiState.isEditEnabled && !isLocked,
                            trailingIcon = {
                                if (isLocked) {
                                    Icon(Icons.Default.Lock, contentDescription = "已鎖定")
                                }
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            InfoColumn("使用度數", "${usage ?: "N/A"}", "度")
                            InfoColumn("預估電費", "${fee?.toInt() ?: "N/A"}", "元")
                        }
                    }
                }
            }
        }
    }
}

// 用於顯示資訊的小元件
@Composable
private fun RowScope.InfoColumn(label: String, value: String, unit: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

// 設定對話框的 Composable (保持不變)
@Composable
fun CalculationSettingsDialog(
    currentSettings: CalculationSettings,
    onDismiss: () -> Unit,
    onSave: (CalculationSettings) -> Unit
) {
    var mode by remember { mutableStateOf(currentSettings.mode) }
    var fixedRate by remember { mutableStateOf(currentSettings.fixedRate.toString()) }
    var tiers by remember { mutableStateOf(currentSettings.tiers) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("計費公式設定") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    RadioButton(selected = mode == CalculationMode.TIERED, onClick = { mode = CalculationMode.TIERED })
                    Text("累進制")
                    Spacer(Modifier.width(24.dp))
                    RadioButton(selected = mode == CalculationMode.FIXED, onClick = { mode = CalculationMode.FIXED })
                    Text("固定制")
                }
                Divider(Modifier.padding(vertical = 8.dp))
                when (mode) {
                    CalculationMode.TIERED -> {
                        Text("總用電級距 (將自動除以房間數)", style = MaterialTheme.typography.titleMedium)
                        tiers.forEachIndexed { index, pair ->
                            val isLastTier = index == tiers.size - 1
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = if (isLastTier) "以上" else pair.first.toInt().toString(),
                                    onValueChange = {
                                        if (!isLastTier) {
                                            val newTiers = tiers.toMutableList()
                                            newTiers[index] = Pair(it.toDoubleOrNull() ?: 0.0, newTiers[index].second)
                                            tiers = newTiers
                                        }
                                    },
                                    label = { Text("級距 ${index + 1} 用量") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    enabled = !isLastTier
                                )
                                OutlinedTextField(
                                    value = pair.second.toString(),
                                    onValueChange = {
                                        val newTiers = tiers.toMutableList()
                                        newTiers[index] = Pair(newTiers[index].first, it.toDoubleOrNull() ?: 0.0)
                                        tiers = newTiers
                                    },
                                    label = { Text("費率") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }
                    CalculationMode.FIXED -> {
                        OutlinedTextField(
                            value = fixedRate,
                            onValueChange = { fixedRate = it },
                            label = { Text("每度電固定單價 (元)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newSettings = CalculationSettings(
                    mode = mode,
                    fixedRate = fixedRate.toFloatOrNull() ?: 5.0f,
                    tiers = tiers
                )
                onSave(newSettings)
            }) { Text("儲存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}