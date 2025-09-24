package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * LandlordHomeScreen 的 UI 狀態資料類別
 */
data class LandlordUiState(
    val landlord: User? = null,
    val announcements: List<Announcement> = emptyList(),
    val repairReports: List<RepairReport> = emptyList(),
    val pendingChangeRequests: List<RoomChangeRequest> = emptyList(),
    val totalRooms: Int = 0, // 【*** 核心修改 1：新增 totalRooms 屬性 ***】
    val isLoading: Boolean = true
)

/**
 * LandlordHomeScreen 的 ViewModel
 * 負責提供房東介面所需的所有資料及操作。
 */
class LandlordViewModel(
    private val landlordId: String,
    private val userDao: UserDao,
    private val announcementDao: AnnouncementDao,
    private val repairReportDao: RepairReportDao,
    private val requestRepository: RoomChangeRequestRepository,
    private val roomRepository: RoomRepository // 【*** 核心修改 2：注入 RoomRepository ***】
) : ViewModel() {

    private val _uiState = MutableStateFlow(LandlordUiState())
    val uiState: StateFlow<LandlordUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userDao.getUserById(landlordId).collectLatest { collectedLandlord ->
                if (collectedLandlord == null) {
                    _uiState.update { it.copy(isLoading = false, landlord = null) }
                    return@collectLatest
                }

                if (collectedLandlord.landlordCode == null) {
                    combine(
                        announcementDao.getAll(),
                        repairReportDao.getAll()
                    ) { announcements, reports ->
                        _uiState.update {
                            it.copy(
                                landlord = collectedLandlord,
                                announcements = announcements,
                                repairReports = reports,
                                pendingChangeRequests = emptyList(),
                                isLoading = false
                            )
                        }
                    }.catch { e ->
                        _uiState.update { it.copy(isLoading = false, landlord = collectedLandlord) }
                    }.collect()
                    return@collectLatest
                }

                // Landlord and landlordCode are valid, proceed to combine all data streams
                combine(
                    announcementDao.getAll(),
                    repairReportDao.getAll(),
                    requestRepository.getRequestsByLandlord(collectedLandlord.landlordCode),
                    roomRepository.getRoomsForLandlord(collectedLandlord.landlordCode) // 【*** 核心修改 3：監聽房間列表 ***】
                ) { announcements, reports, requests, rooms ->
                    LandlordUiState(
                        landlord = collectedLandlord,
                        announcements = announcements,
                        repairReports = reports,
                        pendingChangeRequests = requests.filter { it.status == "pending" },
                        totalRooms = rooms.size, // 【*** 核心修改 4：更新 totalRooms ***】
                        isLoading = false
                    )
                }.catch { e ->
                    _uiState.update { it.copy(isLoading = false, landlord = collectedLandlord) }
                }.collect { state ->
                    _uiState.value = state
                }
            }
        }
    }
}

/**
 * 用於建立 LandlordViewModel 的工廠類別
 */
class LandlordViewModelFactory(
    private val landlordId: String,
    private val db: AppDatabase,
    private val requestRepository: RoomChangeRequestRepository,
    private val roomRepository: RoomRepository // 【*** 核心修改 5：注入 RoomRepository ***】
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LandlordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LandlordViewModel(
                landlordId,
                db.userDao(),
                db.announcementDao(),
                db.repairReportDao(),
                requestRepository,
                roomRepository // 【*** 核心修改 6：傳遞 RoomRepository ***】
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}