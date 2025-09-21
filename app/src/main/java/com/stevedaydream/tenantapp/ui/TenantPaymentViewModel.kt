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
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow() // Expose asStateFlow() for read-only

    init {
        loadPaymentDetails()
    }

    private fun loadPaymentDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            userDao.getUserById(userId).collectLatest { user -> // Collect the Flow<User?>
                if (user == null || user.boundRoomNumber == null) {
                    _uiState.update { it.copy(errorMessage = "您尚未綁定任何房間或找不到使用者", isLoading = false) }
                    return@collectLatest // Stop further processing if no user or no bound room
                }

                val roomNumber = user.boundRoomNumber!! // Safe to use !! because we checked above
                val currentMonth = _uiState.value.currentMonth

                // --- 【*** 核心修改：改用更有效率的查詢 ***】 ---
                // 直接在 IO 執行緒中進行所有資料庫操作
                withContext(Dispatchers.IO) {
                    val room = roomDao.getRoomByNumber(roomNumber)

                    if (room == null) {
                        _uiState.update { it.copy(errorMessage = "找不到對應的房間資料 (房號: $roomNumber)", isLoading = false) }
                        return@withContext
                    }

                    // 取得電費紀錄
                    // Assuming getRecord and getPreviousRecord are suspend functions or return Flow
                    // If they return Flow, you'd collect them here or combine them.
                    // For simplicity, assuming they are suspend functions for now based on previous patterns.
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

                    // 取得繳費狀態
                    // Ensure paymentRepository.getPaymentRecord returns a Flow and collect it
                    paymentRepository.getPaymentRecord(roomNumber, currentMonth).collectLatest { payment ->
                        _uiState.update {
                            it.copy(
                                roomNumber = roomNumber,
                                rentAmount = room.rentAmount,
                                electricityUsage = usage,
                                electricityFee = fee,
                                totalAmount = totalAmount,
                                paymentStatus = if (payment?.isPaid == true) "已繳費" else "未繳費",
                                isLoading = false,
                                errorMessage = null // Clear any previous error
                            )
                        }
                    }
                }
            }
        }
    }
}