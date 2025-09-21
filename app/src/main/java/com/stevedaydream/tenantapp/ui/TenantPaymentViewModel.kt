// tenantapp/ui/TenantPaymentViewModel.kt

package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ViewModel Factory (保持不變)
class TenantPaymentViewModelFactory(
    private val userId: String,
    private val userDao: UserDao,
    private val roomDao: RoomDao,
    private val electricMeterRepository: ElectricMeterRepository, // 改為 Repository
    private val paymentRepository: PaymentRepository // 改為 Repository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TenantPaymentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TenantPaymentViewModel(userId, userDao, roomDao, electricMeterRepository, paymentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class TenantPaymentViewModel(
    private val userId: String,
    private val userDao: UserDao,
    private val roomDao: RoomDao,
    private val electricMeterRepository: ElectricMeterRepository, // 改為 Repository
    private val paymentRepository: PaymentRepository // 改為 Repository
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
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(
        PaymentUiState(
            currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        )
    )
    val uiState: StateFlow<PaymentUiState> = _uiState

    init {
        loadPaymentDetails()
    }

    private fun loadPaymentDetails() {
        viewModelScope.launch {
            val user = userDao.getUserById(userId)
            if (user?.boundRoomNumber == null) {
                _uiState.update { it.copy(errorMessage = "您尚未綁定任何房間", isLoading = false) }
                return@launch
            }

            val roomNumber = user.boundRoomNumber!!
            val currentMonth = _uiState.value.currentMonth

            // --- 【*** 核心修改：改用更有效率的查詢 ***】 ---
            // 直接在 IO 執行緒中進行所有資料庫操作
            withContext(Dispatchers.IO) {
                val room = roomDao.getRoomByNumber(roomNumber)

                if (room == null) {
                    _uiState.update { it.copy(errorMessage = "找不到對應的房間資料", isLoading = false) }
                    return@withContext
                }

                // 取得電費紀錄
                val currentRecord = electricMeterRepository.getRecord(roomNumber, currentMonth) // 使用 Repository
                val previousRecord = electricMeterRepository.getPreviousRecord(roomNumber, currentMonth) // 使用 Repository

                var usage: Int? = null
                var fee: Int? = null
                if (currentRecord != null && previousRecord != null) {
                    usage = currentRecord.meterValue - previousRecord.meterValue
                    if (usage >= 0) {
                        fee = usage * 5 // 每度電 5 元
                    }
                }

                val totalAmount = room.rentAmount + (fee ?: 0)

                // 取得繳費狀態
                paymentRepository.getPaymentRecord(roomNumber, currentMonth).collect { payment -> // 使用 Repository
                    _uiState.update {
                        it.copy(
                            roomNumber = roomNumber,
                            rentAmount = room.rentAmount,
                            electricityUsage = usage,
                            electricityFee = fee,
                            totalAmount = totalAmount,
                            paymentStatus = if (payment?.isPaid == true) "已繳費" else "未繳費",
                            isLoading = false
                        )
                    }
                }
            }
        }
    }
}