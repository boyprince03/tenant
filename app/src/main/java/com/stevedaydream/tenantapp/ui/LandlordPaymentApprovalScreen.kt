// tenantapp/ui/LandlordPaymentApprovalScreen.kt
package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.data.Payment
import com.stevedaydream.tenantapp.data.PaymentRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandlordPaymentApprovalScreen(
    paymentId: String,
    db: AppDatabase,
    navController: NavHostController,
    paymentRepository: PaymentRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paymentDao = db.paymentDao()

    var payment by remember { mutableStateOf<Payment?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isApproving by remember { mutableStateOf(false) }

    LaunchedEffect(paymentId) {
        isLoading = true
        payment = paymentDao.getPaymentRecordNow(paymentId.split("_")[0], paymentId.split("_")[1])
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("繳費審核", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (payment == null) {
                Text("找不到繳費記錄", color = MaterialTheme.colorScheme.error)
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
                            text = "${payment!!.recordMonth} 繳費資訊",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Divider()
                        PaymentInfoRow("房號", payment!!.roomNumber)
                        PaymentInfoRow("應繳總計", "${payment!!.totalAmount} 元", isTotal = true)
                        PaymentInfoRow("繳費狀態", if (payment!!.isPaid) "已繳清" else "待確認", isStatus = true)
                    }
                }

                if (!payment!!.screenshotUrl.isNullOrBlank()) {
                    Text("繳費截圖", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        AsyncImage(
                            model = payment!!.screenshotUrl,
                            contentDescription = "繳費截圖",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Text("租客尚未上傳繳費截圖", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isApproving = true
                            try {
                                val updatedPayment = payment!!.copy(isPaid = true, paymentDate = System.currentTimeMillis())
                                paymentRepository.updatePaymentStatus(updatedPayment)
                                Toast.makeText(context, "已成功確認收款！", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "確認失敗: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isApproving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !payment!!.isPaid && !isApproving
                ) {
                    if (isApproving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = "確認收款", modifier = Modifier.padding(end = 8.dp))
                        Text("確認收款")
                    }
                }
            }
        }
    }
}