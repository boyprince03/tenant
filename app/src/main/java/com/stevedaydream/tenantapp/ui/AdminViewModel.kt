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

    // 【*** 新增狀態 ***】
    val landlords: List<User> = emptyList(),
    val unassignedRooms: List<RoomEntity> = emptyList(),
    val selectedLandlord: User? = null,
    val selectedRoomIdsToAssign: Set<String> = emptySet(),
    val assignmentMessage: String? = null
)

class AdminViewModel(private val adminRepository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    fun loadAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val users = adminRepository.getAllUsers(5)
            val rooms = adminRepository.getAllRooms(5)
            val reports = adminRepository.getAllRepairReports(5)
            val announcements = adminRepository.getAllAnnouncements(5)
            val requests = adminRepository.getAllRoomChangeRequests(5)
            val payments = adminRepository.getAllPayments(5)
            val records = adminRepository.getAllElectricMeterRecords(5)
            // 【*** 新增 ***】
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
                    landlords = landlords, // 【*** 新增 ***】
                    unassignedRooms = unassignedRooms, // 【*** 新增 ***】
                    isLoading = false
                )
            }
        }
    }

    // --- 【*** 以下為新增的方法 ***】 ---

    /**
     * 處理使用者在下拉選單中選擇房東的事件。
     */
    fun onLandlordSelected(landlord: User) {
        _uiState.update { it.copy(selectedLandlord = landlord) }
    }

    /**
     * 處理使用者勾選或取消勾選要指派的房間。
     */
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

    /**
     * 執行指派房間的核心邏輯。
     */
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
                            selectedLandlord = null, // 重設選擇
                            selectedRoomIdsToAssign = emptySet() // 重設勾選
                        )
                    }
                    loadAllData() // 重新載入所有資料以反映變更
                } else {
                    _uiState.update { it.copy(assignmentMessage = "指派失敗，請檢查網路連線。") }
                }
            }
        }
    }

    /**
     * 清除提示訊息，避免重複顯示。
     */
    fun clearAssignmentMessage() {
        _uiState.update { it.copy(assignmentMessage = null) }
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
