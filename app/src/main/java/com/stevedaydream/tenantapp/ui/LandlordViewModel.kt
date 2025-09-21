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
    val isResetting: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * LandlordHomeScreen 的 ViewModel
 * 負責提供房東介面所需的所有資料及操作。
 */
class LandlordViewModel(
    private val landlordId: String,
    userDao: UserDao,
    announcementDao: AnnouncementDao,
    repairReportDao: RepairReportDao,
    requestRepository: RoomChangeRequestRepository,
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LandlordUiState())
    val uiState: StateFlow<LandlordUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val landlord = userDao.getUserById(landlordId)
            if (landlord == null || landlord.landlordCode == null) {
                _uiState.update { it.copy(isLoading = false, landlord = landlord) } // landlord可能為null
                return@launch
            }

            // 結合所有需要的資料流
            combine(
                announcementDao.getAll(),
                repairReportDao.getAll(),
                requestRepository.getRequestsByLandlord(landlord.landlordCode)
            ) { announcements, reports, requests ->
                LandlordUiState(
                    landlord = landlord,
                    announcements = announcements,
                    repairReports = reports,
                    pendingChangeRequests = requests.filter { it.status == "pending" },
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * 重置整個 Firestore 資料庫 (僅供開發使用)
     * @param onResult 操作完成後的回呼，傳回操作是否成功。
     */
    fun resetDatabase(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResetting = true) }
            val success = adminRepository.resetEntireDatabase()
            _uiState.update { it.copy(isResetting = false) }
            onResult(success)
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
    private val adminRepository: AdminRepository
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
                adminRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
