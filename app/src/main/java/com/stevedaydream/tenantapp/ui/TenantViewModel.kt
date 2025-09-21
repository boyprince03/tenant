@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val isRefreshing: Boolean = false, // 【*** 新增 ***】 用於刷新時的 UI 狀態
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
    private val requestRepository: RoomChangeRequestRepository,
    private val userRepository: UserRepository, // 【*** 新增 ***】
    private val roomRepository: RoomRepository  // 【*** 新增 ***】
) : ViewModel() {

    private val _uiState = MutableStateFlow(TenantUiState())
    val uiState: StateFlow<TenantUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }
    /**
     * 【*** 新增此函式 ***】
     * 當使用者手動觸發刷新時呼叫。
     * 此函式會從雲端拉取最新的使用者、房東和房間資料。
     */
    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                // 1. 刷新目前使用者的資料
                userRepository.refreshUser(userId)

                // 2. 從資料庫讀取剛剛刷新後的使用者資料
                val currentUser = userDao.getUserById(userId).first()

                // 3. 根據使用者資料，刷新對應的房東和房間資料
                currentUser?.boundLandlordCode?.let {
                    userRepository.refreshLandlordByCode(it)
                }
                currentUser?.boundRoomNumber?.let {
                    roomRepository.refreshRoomByNumber(it)
                }
                // 資料刷新後，原有的 loadInitialData 中的 Flow 會自動監聽到變更並更新 UI

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "刷新失敗: ${e.message}") }
            } finally {
                // 4. 更新結束，解除刷新狀態
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val userFlow = userDao.getUserById(userId)
            val requestFlow = requestRepository.getLatestRequestByTenantId(userId)

            userFlow.collectLatest { user ->
                if (user == null) {
                    _uiState.update { it.copy(isLoading = false, error = "找不到使用者資料") }
                    return@collectLatest
                }

                // Flows dependent on the user
                val landlordFlow: Flow<User?> = user.boundLandlordCode?.let {
                    userDao.getLandlordByCode(it)
                } ?: flowOf(null)

                // 【*** 错误修复 ***】
                // 将 suspend fun 的结果包装成一个 Flow
                val roomFlow: Flow<RoomEntity?> = user.boundRoomNumber?.let { roomNumber ->
                    flow { emit(roomDao.getRoomByNumber(roomNumber)) }
                } ?: flowOf(null)


                val announcementFlow: Flow<List<Announcement>> = user.boundLandlordCode?.let {
                    announcementDao.getGlobalAndByLandlordCode(it)
                } ?: announcementDao.getGlobalAndByLandlordCode("") // Fallback for global if no landlord code

                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                val paymentFlow: Flow<Payment?> = roomFlow.flatMapLatest { room ->
                    if (room?.roomNumber != null) {
                        paymentDao.getPaymentRecord(room.roomNumber, currentMonth)
                    } else {
                        flowOf(null)
                    }
                }

                // Combine all dependent flows
                combine(
                    requestFlow, // Flow<RoomChangeRequest?>
                    landlordFlow, // Flow<User?>
                    roomFlow,     // Flow<RoomEntity?>
                    paymentFlow,  // Flow<Payment?>
                    announcementFlow // Flow<List<Announcement>>
                ) { latestRequest, landlord, roomDetails, payment, announcements ->

                    val paymentStatusText = if (payment?.isPaid == true) "已繳清" else "未繳費"

                    TenantUiState(
                        currentUser = user, // The user from the outer collectLatest
                        landlord = landlord,
                        roomDetails = roomDetails,
                        announcements = announcements,
                        latestRequest = latestRequest,
                        paymentStatus = paymentStatusText,
                        isLoading = false,
                        error = null
                    )
                }.flowOn(Dispatchers.IO) // Perform combine and DAO operations on IO thread
                    .catch { e ->
                        _uiState.update { it.copy(isLoading = false, error = "載入資料時發生錯誤: ${e.message}") }
                    }
                    .collect { combinedState ->
                        _uiState.value = combinedState
                    }
            }
        }
    }
}

/**
 * 用於建立 TenantViewModel 的工廠類別
 */
class TenantViewModelFactory(
    private val userId: String,
    private val db: AppDatabase,
    private val requestRepository: RoomChangeRequestRepository,
    // 【*** 新增 ***】
    private val userRepository: UserRepository,
    private val roomRepository: RoomRepository
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
                requestRepository,
                userRepository, // 傳遞 UserRepository
                roomRepository  // 傳遞 RoomRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}