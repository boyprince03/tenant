package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantPaymentScreen(
    userId: String,
    navController: NavHostController,
    // 【*** 修正 1：修改函式簽名，直接接收依賴項 ***】
    userDao: UserDao,
    roomDao: RoomDao,
    electricMeterRepository: ElectricMeterRepository,
    paymentRepository: PaymentRepository
) {
    val viewModel: TenantPaymentViewModel = viewModel(
        // 【*** 修正 2：在 Factory 中傳入正確的依賴項 ***】
        factory = TenantPaymentViewModelFactory(userId, userDao, roomDao, electricMeterRepository, paymentRepository)
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("當月繳費查詢") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "發生未知錯誤",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.headlineSmall
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "${uiState.currentMonth} 帳單資訊",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Divider()
                        PaymentInfoRow("房號", uiState.roomNumber ?: "N/A")
                        PaymentInfoRow("當月租金", "${uiState.rentAmount} 元")
                        PaymentInfoRow("本期度數", "${uiState.electricityUsage ?: "未計算"} 度")
                        PaymentInfoRow("應繳電費", "${uiState.electricityFee ?: "未計算"} 元")
                        Divider()
                        PaymentInfoRow("總計金額", "${uiState.totalAmount ?: "未計算"} 元", isTotal = true)
                        PaymentInfoRow("繳費狀態", uiState.paymentStatus, isStatus = true)
                    }
                }
            }
        }
    }
}

// PaymentInfoRow 保持不變，此處省略

@Composable
fun PaymentInfoRow(label: String, value: String, isTotal: Boolean = false, isStatus: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = if (isTotal) 20.sp else 16.sp
            ),
            fontWeight = FontWeight.Bold,
            color = if (isStatus) {
                if (value == "已繳費") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            } else {
                LocalContentColor.current
            }
        )
    }
}