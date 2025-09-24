package com.stevedaydream.tenantapp.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context

// ViewModel Factory (保持不變)
class TenantPaymentViewModelFactory(
    private val userId: String,
    private val userDao: UserDao,
    private val roomDao: RoomDao,
    private val electricMeterRepository: ElectricMeterRepository,
    private val paymentRepository: PaymentRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TenantPaymentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TenantPaymentViewModel(userId, userDao, roomDao, electricMeterRepository, paymentRepository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class TenantPaymentViewModel(
    private val userId: String,
    private val userDao: UserDao,
    private val roomDao: RoomDao,
    private val electricMeterRepository: ElectricMeterRepository,
    private val paymentRepository: PaymentRepository,
    private val context: Context
) : ViewModel() {

    data class PaymentUiState(
        val roomNumber: String? = null,
        val rentAmount: Int = 0,
        val currentMonth: String,
        val electricityUsage: Int? = null,
        val electricityFee: Int? = null,
        val totalAmount: Int? = null,
        val paymentStatus: String = "查詢中...",
        val isLoading: Boolean = true,
        val errorMessage: String? = null,
        val isUploading: Boolean = false
    )

    private val _uiState = MutableStateFlow(
        PaymentUiState(
            currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        )
    )
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val functions = FirebaseFunctions.getInstance()
    private val storage = Firebase.storage

    init {
        loadPaymentDetails()
    }

    private fun loadPaymentDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            userDao.getUserById(userId).collectLatest { user ->
                if (user == null || user.boundRoomNumber == null) {
                    _uiState.update { it.copy(errorMessage = "您尚未綁定任何房間或找不到使用者", isLoading = false) }
                    return@collectLatest
                }

                val roomNumber = user.boundRoomNumber!!
                val currentMonth = _uiState.value.currentMonth

                withContext(Dispatchers.IO) {
                    val room = roomDao.getRoomByNumber(roomNumber)
                    if (room == null) {
                        _uiState.update { it.copy(errorMessage = "找不到對應的房間資料 (房號: $roomNumber)", isLoading = false) }
                        return@withContext
                    }

                    val currentRecord = electricMeterRepository.getRecord(roomNumber, currentMonth)
                    val previousRecord = electricMeterRepository.getPreviousRecord(roomNumber, currentMonth)

                    var usage: Int? = null
                    var fee: Int? = null
                    if (currentRecord != null && previousRecord != null) {
                        usage = currentRecord.meterValue - previousRecord.meterValue
                        if (usage >= 0) {
                            fee = usage * 5 // 每度電 5 元
                        }
                    }

                    val totalAmount = room.rentAmount + (fee ?: 0)

                    paymentRepository.getPaymentRecord(roomNumber, currentMonth).collectLatest { payment ->
                        _uiState.update {
                            it.copy(
                                roomNumber = roomNumber,
                                rentAmount = room.rentAmount,
                                electricityUsage = usage,
                                electricityFee = fee,
                                totalAmount = totalAmount,
                                paymentStatus = if (payment?.isPaid == true) "已繳清" else "未繳費",
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 處理繳費截圖上傳和後續的通知。
     */
    fun onUploadScreenshot(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null) }
            try {
                val uiStateValue = _uiState.value
                val roomNumber = uiStateValue.roomNumber!!
                val currentMonth = uiStateValue.currentMonth
                val paymentId = "${roomNumber}_${currentMonth}"

                // 【*** 修正點 1：在檔案上傳前，先確保 Payment 物件存在並有 ID ***】
                val paymentToSave = Payment(
                    id = paymentId,
                    roomNumber = roomNumber,
                    recordMonth = currentMonth,
                    rentAmount = uiStateValue.rentAmount,
                    electricityFee = uiStateValue.electricityFee ?: 0,
                    totalAmount = uiStateValue.totalAmount ?: 0,
                    isPaid = false,
                    screenshotUrl = null
                )
                paymentRepository.insertOrUpdate(paymentToSave)
                // 從 repository 中再讀取一次，確保 ID 已經正確設定
                val updatedPayment = paymentRepository.getPaymentRecord(roomNumber, currentMonth).firstOrNull()
                if (updatedPayment == null) {
                    throw Exception("建立繳費記錄後仍找不到，請重試。")
                }

                // 【*** 修正點 2：使用 Payment 的 id 作為資料夾名稱，增加路徑穩定性 ***】
                val storageRef = storage.reference.child("payments/${updatedPayment.id}/${UUID.randomUUID()}.jpg")

                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    storageRef.putStream(inputStream).await()
                } ?: throw Exception("無法讀取圖片檔案。")

                val imageUrl = storageRef.downloadUrl.await().toString()

                val finalPayment = updatedPayment.copy(screenshotUrl = imageUrl, isPaid = false)
                paymentRepository.insertOrUpdate(finalPayment)

                val user = userDao.getUserById(userId).firstOrNull()
                val landlordId = user?.boundLandlordCode?.let {
                    val firestore = FirebaseFirestore.getInstance()
                    firestore.collection("users").whereEqualTo("landlordCode", it).get().await().documents.firstOrNull()?.id
                }

                if (landlordId != null) {
                    functions.getHttpsCallable("sendPaymentNotification").call(
                        hashMapOf(
                            "landlordId" to landlordId,
                            "tenantName" to user?.username,
                            "roomNumber" to roomNumber,
                            "paymentId" to finalPayment.id
                        )
                    ).await()
                }

                _uiState.update { it.copy(isUploading = false, errorMessage = null, paymentStatus = "已上傳截圖，待房東確認") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploading = false, errorMessage = "上傳失敗: ${e.message}") }
            }
        }
    }
}