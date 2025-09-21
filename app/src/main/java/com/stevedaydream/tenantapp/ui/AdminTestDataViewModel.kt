package com.stevedaydream.tenantapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import kotlin.random.Random

data class TestDataUiState(
    val isLoading: Boolean = false,
    val message: String = "",
    val isError: Boolean = false
)

class AdminTestDataViewModel(private val adminRepository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TestDataUiState())
    val uiState: StateFlow<TestDataUiState> = _uiState.asStateFlow()

    var userCount by mutableStateOf("3")
    var roomCount by mutableStateOf("3")
    var announcementCount by mutableStateOf("3")
    var repairReportCount by mutableStateOf("3")

    private fun generateData(
        countStr: String,
        generator: (Int) -> List<Any>,
        inserter: suspend (List<Any>) -> Pair<Boolean, String>
    ) {
        val count = countStr.toIntOrNull()
        if (count == null || count <= 0) {
            _uiState.update { it.copy(message = "請輸入有效的數量", isError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "", isError = false) }
            val data = generator(count)
            val (success, message) = inserter(data)
            _uiState.update { it.copy(isLoading = false, message = message, isError = !success) }
        }
    }

    fun generateUsers() {
        generateData(userCount, this::createRandomUsers) { data ->
            adminRepository.insertTestUsers(data as List<User>)
        }
    }

    fun generateRooms() {
        generateData(roomCount, this::createRandomRooms) { data ->
            adminRepository.insertTestRooms(data as List<RoomEntity>)
        }
    }

    fun generateAnnouncements() {
        generateData(announcementCount, this::createRandomAnnouncements) { data ->
            adminRepository.insertTestAnnouncements(data as List<Announcement>)
        }
    }

    fun generateRepairReports() {
        generateData(repairReportCount, this::createRandomRepairReports) { data ->
            adminRepository.insertTestRepairReports(data as List<RepairReport>)
        }
    }

    // --- Private Helper Functions for Data Generation ---

    private fun createRandomUsers(count: Int): List<User> {
        val firstNames = listOf("陳", "林", "黃", "張", "李")
        val lastNames = listOf("家豪", "志明", "俊傑", "雅婷", "美玲")
        val roles = listOf("tenant", "landlord")
        return List(count) {
            val role = roles.random()
            User(
                id = UUID.randomUUID().toString(),
                username = "${firstNames.random()}${lastNames.random()}",
                phone = "09${Random.nextInt(10000000, 99999999)}",
                idNumber = "A${Random.nextInt(100000000, 200000000)}",
                role = role,
                landlordCode = if (role == "landlord") UUID.randomUUID().toString().take(8).uppercase() else null
            )
        }
    }

    private fun createRandomRooms(count: Int): List<RoomEntity> {
        val types = listOf("套房", "雅房", "家庭式")
        val statuses = listOf("可租", "出租中", "維修中")
        return List(count) {
            RoomEntity(
                id = UUID.randomUUID().toString(),
                roomNumber = "${Random.nextInt(1, 10)}${String.format("%02d", Random.nextInt(1, 10))}",
                type = types.random(),
                status = statuses.random(),
                rentAmount = Random.nextInt(5000, 20000) / 500 * 500
            )
        }
    }

    private fun createRandomAnnouncements(count: Int): List<Announcement> {
        val titles = listOf("停水通知", "電費調整", "中秋節快樂", "社區消毒")
        val contents = listOf("因大樓清洗水塔，明日將停水。", "下期電費將依台電公告調整。", "祝各位住戶中秋佳節愉快。", "本週末將進行公共區域消毒。")
        return List(count) {
            Announcement(
                id = UUID.randomUUID().toString(),
                title = titles.random(),
                content = contents.random(),
                date = System.currentTimeMillis() - Random.nextLong(0, 86400000L * 30)
            )
        }
    }

    private fun createRandomRepairReports(count: Int): List<RepairReport> {
        val issues = listOf("燈泡不亮", "水管堵塞", "冷氣不冷", "網路斷線")
        val statuses = listOf("待處理", "處理中", "已完成")
        return List(count) {
            RepairReport(
                id = UUID.randomUUID().toString(),
                tenantName = "房客${Random.nextInt(1, 20)}",
                roomNumber = "${Random.nextInt(1, 10)}${String.format("%02d", Random.nextInt(1, 10))}",
                issue = issues.random(),
                description = "問題如標題，請盡快處理。",
                status = statuses.random(),
                date = System.currentTimeMillis() - Random.nextLong(0, 86400000L * 10)
            )
        }
    }
}


class AdminTestDataViewModelFactory(
    private val adminRepository: AdminRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminTestDataViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdminTestDataViewModel(adminRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}