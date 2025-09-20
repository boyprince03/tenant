package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// --- ViewModel Factory ---
class ElectricityQueryViewModelFactory(
    private val userId: Int,
    private val db: AppDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ElectricityQueryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ElectricityQueryViewModel(userId, db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- ViewModel ---
class ElectricityQueryViewModel(
    private val userId: Int,
    private val db: AppDatabase
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
        val errorMessage: String? = null
    )

    // --- StateFlows ---
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        loadInitialData()
    }

    // --- Event Handlers ---
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
            try {
                val user = db.userDao().getUserById(userId) ?: throw IllegalStateException("User not found")
                val isLandlord = user.role == "landlord"

                val rooms = if (isLandlord) {
                    db.roomDao().getRoomsByLandlordCode(user.landlordCode ?: "")
                } else {
                    user.boundRoomNumber?.let { db.roomDao().getRoomByNumber(it) }?.let { listOf(it) } ?: emptyList()
                }

                if (rooms.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = if(isLandlord) "您尚未建立任何房間" else "您尚未綁定房間") }
                    return@launch
                }

                val roomNumbers = rooms.map { it.roomNumber }
                val allRecords = db.electricMeterDao().getRecordsForRooms(roomNumbers)

                val months = allRecords.map { it.recordMonth }.distinct().let { months ->
                    if (!isLandlord && rooms.isNotEmpty()) {
                        val rentStartDate = rooms.first().rentStartDate
                        filterMonthsSince(months, rentStartDate)
                    } else {
                        months
                    }
                }.sortedDescending()

                _uiState.update {
                    it.copy(
                        isLandlord = isLandlord,
                        availableRooms = rooms,
                        availableMonths = months,
                        selectedMonth = months.firstOrNull() ?: "",
                        selectedRooms = roomNumbers.toSet(),
                        isLoading = false
                    )
                }
                recalculateResults() // Initial calculation
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "讀取資料失敗: ${e.message}") }
            }
        }
    }

    private fun recalculateResults() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = _uiState.value
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
        } catch (e: Exception) {
            months // parsing error, return original list
        }
    }
}