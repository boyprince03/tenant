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
    val payments: List<Payment> = emptyList(), // 新增
    val electricMeterRecords: List<ElectricMeterRecord> = emptyList(), // 新增
    val isLoading: Boolean = true
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
            val payments = adminRepository.getAllPayments(5) // 新增
            val records = adminRepository.getAllElectricMeterRecords(5) // 新增

            _uiState.update {
                it.copy(
                    users = users,
                    rooms = rooms,
                    repairReports = reports,
                    announcements = announcements,
                    roomChangeRequests = requests,
                    payments = payments, // 新增
                    electricMeterRecords = records, // 新增
                    isLoading = false
                )
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