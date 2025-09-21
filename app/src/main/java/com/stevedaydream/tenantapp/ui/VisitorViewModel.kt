package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.Announcement
import com.stevedaydream.tenantapp.data.AnnouncementDao
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.RoomEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * VisitorHomeScreen 的 UI 狀態資料類別
 */
data class VisitorUiState(
    val announcements: List<Announcement> = emptyList(),
    val availableRooms: List<RoomEntity> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * VisitorHomeScreen 的 ViewModel
 * 負責提供訪客介面所需的公告和房間列表。
 */
class VisitorViewModel(
    announcementDao: AnnouncementDao,
    roomDao: RoomDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisitorUiState())
    val uiState: StateFlow<VisitorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 使用 combine 將兩個 Flow 合併，當任一資料來源變動時，都會觸發更新
            combine(
                announcementDao.getAll(), // 取得所有公告的 Flow
                roomDao.getAllRooms()     // 取得所有房間的 Flow
            ) { announcements, rooms ->
                // 在這裡處理資料轉換，產生新的 UI 狀態
                VisitorUiState(
                    announcements = announcements,
                    availableRooms = rooms.filter { it.status.contains("可租", ignoreCase = true) }, // 只顯示可租的房間
                    isLoading = false
                )
            }.collect { state ->
                // 將最新的狀態發送給 UI
                _uiState.value = state
            }
        }
    }
}

/**
 * 用於建立 VisitorViewModel 的工廠類別
 */
class VisitorViewModelFactory(
    private val announcementDao: AnnouncementDao,
    private val roomDao: RoomDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VisitorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VisitorViewModel(announcementDao, roomDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
