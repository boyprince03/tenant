package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * TenantHomeScreen 的 UI 狀態資料類別
 */
data class TenantUiState(
    val currentUser: User? = null,
    val landlord: User? = null,
    val roomDetails: RoomEntity? = null,
    val announcements: List<Announcement> = emptyList(),
    val latestRequest: RoomChangeRequest? = null,
    val paymentStatus: String = "查詢中...",
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * TenantHomeScreen 的 ViewModel
 * 負責提供租客介面所需的所有資料。
 */
class TenantViewModel(
    private val userId: String,
    private val userDao: UserDao,
    private val roomDao: RoomDao,
    private val announcementDao: AnnouncementDao,
    private val paymentDao: PaymentDao,
    private val requestRepository: RoomChangeRequestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TenantUiState())
    val uiState: StateFlow<TenantUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // 監聽使用者資料的變化
            val userFlow = flow { emit(userDao.getUserById(userId)) }

            // 監聽最新的換房請求
            val requestFlow = requestRepository.getLatestRequestByTenantId(userId)

            // 將使用者和請求的 Flow 結合起來
            combine(userFlow, requestFlow) { user, request ->
                if (user == null) {
                    _uiState.update { it.copy(isLoading = false, error = "找不到使用者資料") }
                    return@combine
                }

                // 根據使用者資料，進一步取得房東、房間和繳費資訊
                val landlord = user.boundLandlordCode?.let { userDao.getLandlordByCode(it) }
                val room = user.boundRoomNumber?.let { roomDao.getRoomByNumber(it) }

                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                val payment = room?.roomNumber?.let { paymentDao.getPaymentRecord(it, currentMonth).firstOrNull() }
                val paymentStatus = if (payment?.isPaid == true) "已繳清" else "未繳費"

                // 根據房東 code 取得公告的 Flow
                val announcementFlow = user.boundLandlordCode?.let {
                    announcementDao.getGlobalAndByLandlordCode(it)
                } ?: announcementDao.getGlobalAndByLandlordCode("")

                // 監聽公告的變化並更新最終狀態
                announcementFlow.collect { announcements ->
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            landlord = landlord,
                            roomDetails = room,
                            latestRequest = request,
                            paymentStatus = paymentStatus,
                            announcements = announcements,
                            isLoading = false,
                            error = null // 成功載入後清除錯誤
                        )
                    }
                }
            }.flowOn(Dispatchers.IO).launchIn(viewModelScope) // 在 IO 執行緒執行資料庫操作
        }
    }
}

/**
 * 用於建立 TenantViewModel 的工廠類別
 */
class TenantViewModelFactory(
    private val userId: String,
    private val db: AppDatabase,
    private val requestRepository: RoomChangeRequestRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TenantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TenantViewModel(
                userId,
                db.userDao(),
                db.roomDao(),
                db.announcementDao(),
                db.paymentDao(),
                requestRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
