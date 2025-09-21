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
    val isLoading: Boolean = true
)

/**
 * LandlordHomeScreen 的 ViewModel
 * 負責提供房東介面所需的所有資料及操作。
 */
class LandlordViewModel(
    private val landlordId: String,
    private val userDao: UserDao, // Store as property if used elsewhere, or pass directly
    private val announcementDao: AnnouncementDao,
    private val repairReportDao: RepairReportDao,
    private val requestRepository: RoomChangeRequestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LandlordUiState())
    val uiState: StateFlow<LandlordUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            userDao.getUserById(landlordId).collectLatest { collectedLandlord ->
                if (collectedLandlord == null) {
                    _uiState.update { it.copy(isLoading = false, landlord = null) }
                    return@collectLatest // Exit if landlord is not found
                }

                // Check for landlordCode after confirming landlord is not null
                if (collectedLandlord.landlordCode == null) {
                    // Landlord exists but is not properly configured as a landlord (no landlordCode)
                    // Update UI to show landlord info but indicate that landlord-specific data can't be loaded.
                    // Or, treat as an error/incomplete state depending on business logic.
                    _uiState.update { it.copy(isLoading = false, landlord = collectedLandlord, announcements = emptyList(), repairReports = emptyList(), pendingChangeRequests = emptyList()) }
                    // Optionally, you might want to fetch announcements and repair reports that are not landlord-specific if any.
                    // For now, assuming if landlordCode is null, we stop further landlord-specific loading.
                     combine(
                        announcementDao.getAll(), // Assuming these are general, not landlord-specific
                        repairReportDao.getAll()  // Assuming these are general, not landlord-specific
                    ) { announcements, reports ->
                        _uiState.update {
                            it.copy(
                                landlord = collectedLandlord,
                                announcements = announcements,
                                repairReports = reports,
                                pendingChangeRequests = emptyList(), // No requests if no landlord code
                                isLoading = false
                            )
                        }
                    }.catch { e -> 
                        // Handle exceptions from combine or its inner flows
                         _uiState.update { it.copy(isLoading = false, landlord = collectedLandlord) } 
                    }.collect()
                    return@collectLatest
                }

                // Landlord and landlordCode are valid, proceed to combine all data streams
                combine(
                    announcementDao.getAll(),
                    repairReportDao.getAll(),
                    requestRepository.getRequestsByLandlord(collectedLandlord.landlordCode) // Safe to use landlordCode here
                ) { announcements, reports, requests ->
                    LandlordUiState(
                        landlord = collectedLandlord,
                        announcements = announcements,
                        repairReports = reports,
                        pendingChangeRequests = requests.filter { it.status == "pending" },
                        isLoading = false
                    )
                }.catch { e ->
                     // Handle exceptions from combine or its inner flows
                    _uiState.update { it.copy(isLoading = false, landlord = collectedLandlord) } // Update with what we have
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
    private val requestRepository: RoomChangeRequestRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LandlordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LandlordViewModel(
                landlordId,
                db.userDao(),
                db.announcementDao(),
                db.repairReportDao(),
                requestRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
