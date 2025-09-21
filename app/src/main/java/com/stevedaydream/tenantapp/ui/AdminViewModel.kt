package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val users: List<User> = emptyList(),
    val rooms: List<RoomEntity> = emptyList(),
    val repairReports: List<RepairReport> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val roomChangeRequests: List<RoomChangeRequest> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val electricMeterRecords: List<ElectricMeterRecord> = emptyList(),
    val isLoading: Boolean = true,

    val landlords: List<User> = emptyList(),
    val unassignedRooms: List<RoomEntity> = emptyList(),
    val selectedLandlord: User? = null,
    val selectedRoomIdsToAssign: Set<String> = emptySet(),
    val assignmentMessage: String? = null,

    val isResetting: Boolean = false,
    val isSyncing: Boolean = false // Added
)

class AdminViewModel(private val adminRepository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val users = adminRepository.getAllUsers(5)
                val rooms = adminRepository.getAllRooms(5)
                val reports = adminRepository.getAllRepairReports(5)
                val announcements = adminRepository.getAllAnnouncements(5)
                val requests = adminRepository.getAllRoomChangeRequests(5)
                val payments = adminRepository.getAllPayments(5)
                val records = adminRepository.getAllElectricMeterRecords(5)
                val landlords = adminRepository.getAllLandlords()
                val unassignedRooms = adminRepository.getUnassignedRooms()

                _uiState.update {
                    it.copy(
                        users = users,
                        rooms = rooms,
                        repairReports = reports,
                        announcements = announcements,
                        roomChangeRequests = requests,
                        payments = payments,
                        electricMeterRecords = records,
                        landlords = landlords,
                        unassignedRooms = unassignedRooms,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onLandlordSelected(landlord: User) {
        _uiState.update { it.copy(selectedLandlord = landlord) }
    }

    fun onRoomToAssignSelectionChanged(roomId: String) {
        _uiState.update {
            val currentSelection = it.selectedRoomIdsToAssign.toMutableSet()
            if (roomId in currentSelection) {
                currentSelection.remove(roomId)
            } else {
                currentSelection.add(roomId)
            }
            it.copy(selectedRoomIdsToAssign = currentSelection)
        }
    }

    fun assignSelectedRooms() {
        viewModelScope.launch {
            val landlord = _uiState.value.selectedLandlord
            val roomIds = _uiState.value.selectedRoomIdsToAssign.toList()

            if (landlord?.landlordCode != null && roomIds.isNotEmpty()) {
                val success = adminRepository.assignRoomsToLandlord(roomIds, landlord.landlordCode)
                if (success) {
                    _uiState.update {
                        it.copy(
                            assignmentMessage = "成功指派 ${roomIds.size} 間房間給 ${landlord.username}",
                            selectedLandlord = null,
                            selectedRoomIdsToAssign = emptySet()
                        )
                    }
                    loadAllData()
                } else {
                    _uiState.update { it.copy(assignmentMessage = "指派失敗，請檢查網路連線。") }
                }
            }
        }
    }

    fun clearAssignmentMessage() {
        _uiState.update { it.copy(assignmentMessage = null) }
    }

    fun resetDatabase(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResetting = true) }
            val (success, message) = adminRepository.resetEntireDatabase()
            _uiState.update { it.copy(isResetting = false) }
            onResult(success, message)
        }
    }

    fun resetLocalDatabase(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResetting = true) }
            val (success, message) = adminRepository.resetLocalDatabase()
            _uiState.update { it.copy(isResetting = false) }
            onResult(success, message)
        }
    }

    /**
     * 【*** 新增此方法 ***】
     * 將所有雲端資料同步至本地。
     */
    fun syncAllData(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val (success, message) = adminRepository.syncAllDataFromCloud()
            _uiState.update { it.copy(isSyncing = false) }
            onResult(success, message)
            if(success) {
                loadAllData() // 同步成功後重新整理儀表板的預覽資料
            }
        }
    }
}

class AdminViewModelFactory(private val adminRepository: AdminRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdminViewModel(adminRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}