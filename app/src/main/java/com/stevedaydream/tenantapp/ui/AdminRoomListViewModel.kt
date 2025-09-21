package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.AdminRepository
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminRoomListUiState(
    val roomGroups: Map<User?, List<RoomEntity>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class AdminRoomListViewModel(private val adminRepository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminRoomListUiState())
    val uiState: StateFlow<AdminRoomListUiState> = _uiState.asStateFlow()

    init {
        loadAllRoomsGroupedByLandlord()
    }

    private fun loadAllRoomsGroupedByLandlord() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val allRooms = adminRepository.getAllRooms()
                val allLandlords = adminRepository.getAllLandlords()

                // 使用 landlordCode 作為 key 來建立一個 map
                val landlordMap = allLandlords.associateBy { it.landlordCode }

                // 根據 landlordCode 將房間分組
                val groupedByLandlordCode = allRooms.groupBy { it.landlordCode }

                // 建立最終的 Map<User?, List<RoomEntity>>
                val finalGroups = mutableMapOf<User?, List<RoomEntity>>()
                groupedByLandlordCode.forEach { (landlordCode, rooms) ->
                    if (landlordCode == null) {
                        // key 為 null 代表未指派的房間
                        finalGroups[null] = rooms
                    } else {
                        val landlord = landlordMap[landlordCode]
                        if (landlord != null) {
                            finalGroups[landlord] = rooms
                        } else {
                            // 如果有房間的 landlordCode 在房東列表找不到，也歸類到未指派
                            val unassigned = finalGroups.getOrPut(null) { emptyList() }
                            finalGroups[null] = unassigned + rooms
                        }
                    }
                }

                _uiState.update { it.copy(isLoading = false, roomGroups = finalGroups) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "讀取房間資料失敗: ${e.message}") }
            }
        }
    }
}

class AdminRoomListViewModelFactory(private val adminRepository: AdminRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminRoomListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdminRoomListViewModel(adminRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}