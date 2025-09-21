package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
// import kotlinx.coroutines.withContext // Unused import removed
import java.text.SimpleDateFormat
import java.util.*

// --- ViewModel Factory ---
class ElectricityQueryViewModelFactory(
    private val userId: String,
    private val db: AppDatabase,
    // 【*** 核心修改 1：接收 Repository ***】
    private val meterRepository: ElectricMeterRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ElectricityQueryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // 【*** 核心修改 2：將 Repository 傳給 ViewModel ***】
            return ElectricityQueryViewModel(userId, db, meterRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- ViewModel ---
class ElectricityQueryViewModel(
    private val userId: String,
    private val db: AppDatabase,
    // 【*** 核心修改 3：持有 Repository 的實例 ***】
    private val meterRepository: ElectricMeterRepository
) : ViewModel() {

    // --- Data Classes for UI State ---
    data class QueryResult(
        val roomNumber: String,
        val usage: Int,
        val fee: Int,
        val paymentStatus: String,
        val recordMonth: String,
        val meterValue: Int,
        val previousMeterValue: Int
    )

    data class UiState(
        val isLandlord: Boolean = false,
        val isLoading: Boolean = true,
        val availableRooms: List<RoomEntity> = emptyList(),
        val availableMonths: List<String> = emptyList(),
        val selectedRooms: Set<String> = emptySet(),
        val selectedMonth: String = "",
        val queryResults: List<QueryResult> = emptyList(),
        val errorMessage: String? = null,
        // 【*** 核心修改 4：新增同步狀態 ***】
        val isSyncing: Boolean = false
    )

    // --- StateFlows ---
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    // 【*** 核心修改 5：新增一個 Flow 用於顯示 Toast 訊息 ***】
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage

    init {
        loadInitialData()
    }


    // --- Event Handlers ---
    /**
     * 【*** 核心修改 6：新增處理同步按鈕點擊的函式 ***】
     */
    fun onSyncDataClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val (success, message) = meterRepository.syncAllRecordsFromFirestore()
            _toastMessage.emit(message) // 發送訊息給 UI 顯示 Toast
            _uiState.update { it.copy(isSyncing = false) }
            if (success) {
                // 同步成功後，重新載入頁面資料以反映最新結果
                loadInitialData()
            }
        }
    }

    fun onRoomSelectionChanged(roomNumber: String) {
        val newSelectedRooms = _uiState.value.selectedRooms.toMutableSet()
        if (roomNumber in newSelectedRooms) {
            newSelectedRooms.remove(roomNumber)
        } else {
            newSelectedRooms.add(roomNumber)
        }
        _uiState.update { it.copy(selectedRooms = newSelectedRooms) }
        recalculateResults()
    }

    fun onSelectAllRooms(selectAll: Boolean) {
        val allRoomNumbers = _uiState.value.availableRooms.map { it.roomNumber }.toSet()
        _uiState.update { it.copy(selectedRooms = if (selectAll) allRoomNumbers else emptySet()) }
        recalculateResults()
    }

    fun onMonthSelected(month: String) {
        _uiState.update { it.copy(selectedMonth = month) }
        recalculateResults()
    }

    // --- Private Logic ---
    private fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) } // Set loading true at the start
            db.userDao().getUserById(userId).collectLatest { user ->
                if (user == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "無法載入使用者資料") }
                    return@collectLatest
                }

                try {
                    val isLandlord = user.role == "landlord"

                    val rooms = if (isLandlord) {
                        db.roomDao().getRoomsByLandlordCode(user.landlordCode ?: "")
                    } else {
                        user.boundRoomNumber?.let { roomNum -> db.roomDao().getRoomByNumber(roomNum) }?.let { room -> listOf(room) } ?: emptyList()
                    }

                    if (rooms.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, isLandlord = isLandlord, errorMessage = if(isLandlord) "您尚未建立任何房間" else "您尚未綁定房間") }
                        return@collectLatest
                    }

                    val roomNumbers = rooms.map { it.roomNumber }
                    val allRecords = db.electricMeterDao().getRecordsForRooms(roomNumbers)

                    val months = allRecords.map { it.recordMonth }.distinct().let { monthStrings ->
                        if (!isLandlord) { // rooms.isNotEmpty() check removed as it's always true here
                            val rentStartDate = rooms.first().rentStartDate
                            filterMonthsSince(monthStrings, rentStartDate)
                        } else {
                            monthStrings
                        }
                    }.sortedDescending()

                    _uiState.update {
                        it.copy(
                            isLandlord = isLandlord,
                            availableRooms = rooms,
                            availableMonths = months,
                            selectedMonth = months.firstOrNull() ?: "",
                            selectedRooms = roomNumbers.toSet(),
                            isLoading = false,
                            errorMessage = null // Clear previous error messages
                        )
                    }
                    recalculateResults() // Initial calculation
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "讀取資料失敗: ${e.message}") }
                }
            }
        }
    }

    private fun recalculateResults() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value
            if (currentState.isLoading) return@launch // Don't recalculate if initial data isn't loaded
            if (currentState.selectedMonth.isBlank() || currentState.selectedRooms.isEmpty()) {
                _uiState.update { it.copy(queryResults = emptyList()) }
                return@launch
            }

            val results = mutableListOf<QueryResult>()
            for (roomNo in currentState.selectedRooms) {
                val currentRecord = db.electricMeterDao().getRecord(roomNo, currentState.selectedMonth)
                val previousRecord = db.electricMeterDao().getPreviousRecord(roomNo, currentState.selectedMonth)

                if (currentRecord != null) {
                    val usage = if (previousRecord != null) currentRecord.meterValue - previousRecord.meterValue else 0
                    val fee = if (usage >= 0) usage * 5 else 0 // 假設每度電 5 元
                    val payment = db.paymentDao().getPaymentRecordNow(roomNo, currentState.selectedMonth)
                    val status = if (payment?.isPaid == true) "已繳費" else "未繳費"

                    results.add(
                        QueryResult(
                            roomNumber = roomNo,
                            usage = if(usage >= 0) usage else 0,
                            fee = fee,
                            paymentStatus = status,
                            recordMonth = currentRecord.recordMonth,
                            meterValue = currentRecord.meterValue,
                            previousMeterValue = previousRecord?.meterValue ?: 0
                        )
                    )
                }
            }
            _uiState.update { it.copy(queryResults = results.sortedBy { res -> res.roomNumber }) }
        }
    }

    private fun filterMonthsSince(months: List<String>, startDate: String): List<String> {
        if (startDate.isBlank()) return months
        return try {
            val startDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val startDateCal = Calendar.getInstance().apply { time = startDateFormat.parse(startDate)!! }
            val startYear = startDateCal.get(Calendar.YEAR)
            val startMonth = startDateCal.get(Calendar.MONTH)

            months.filter {
                val monthDate = monthFormat.parse(it)!!
                val monthCal = Calendar.getInstance().apply { time = monthDate }
                val monthYear = monthCal.get(Calendar.YEAR)
                val month = monthCal.get(Calendar.MONTH)
                monthYear > startYear || (monthYear == startYear && month >= startMonth)
            }
        } catch (_: Exception) { // Renamed e to _
            months // parsing error, return original list
        }
    }
}