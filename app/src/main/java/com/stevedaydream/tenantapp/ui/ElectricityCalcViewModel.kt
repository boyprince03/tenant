// tenantapp/ui/ElectricityCalcViewModel.kt
package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

// --- Factory ---
// 【*** 修正：Factory 的 meterDao 參數改為 meterRepository ***】
class ElectricityCalcViewModelFactory(
    private val roomDao: RoomDao,
    private val meterRepository: ElectricMeterRepository, // 改為 Repository
    private val userRole: String,
    private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ElectricityCalcViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ElectricityCalcViewModel(roomDao, meterRepository, userRole, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ElectricityCalcViewModel(
    private val roomDao: RoomDao,
    private val meterRepository: ElectricMeterRepository, // 改為 Repository
    private val userRole: String,
    private val settingsManager: SettingsManager
) : ViewModel() {

    data class UiState(
        val isEditEnabled: Boolean = true,
        val currentMonth: String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()),
        val showMonthPicker: Boolean = false,
        val roomList: List<RoomEntity> = emptyList(),
        val meterMap: Map<String, String> = emptyMap(),
        val lockedRoomMap: Map<String, Boolean> = emptyMap(),
        val usedMap: Map<String, Int> = emptyMap(),
        val feeMap: Map<String, Float> = emptyMap(),
        val canSave: Boolean = false,
        val message: String = "",
        val messageType: MessageType = MessageType.Info,
        val showSettingsDialog: Boolean = false,
        val settings: CalculationSettings? = null
    )

    enum class MessageType { Success, Error, Info }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    init {
        _uiState.update { it.copy(isEditEnabled = (userRole == "landlord")) }

        viewModelScope.launch {
            roomDao.getAllRooms().combine(settingsManager.settingsFlow) { rooms, settings ->
                Pair(rooms, settings)
            }.collect { (rooms, settings) ->
                _uiState.update { it.copy(roomList = rooms, settings = settings) }
                loadDataForMonth(_uiState.value.currentMonth)
            }
        }
    }

    private fun loadDataForMonth(month: String) {
        viewModelScope.launch {
            val rooms = _uiState.value.roomList
            val settings = _uiState.value.settings ?: return@launch
            // 【*** 修正：使用 meterRepository 並正確處理 suspend ***】
            val recordsForMonth = rooms.map { room ->
                meterRepository.getRecord(room.roomNumber, month)
            }.filterNotNull()

            val meterMapForLoadedMonth = rooms.associate { room ->
                val record = recordsForMonth.find { it.roomNumber == room.roomNumber }
                room.roomNumber to (record?.meterValue?.toString() ?: "")
            }.toMutableMap()
            val lockedRoomMapForLoadedMonth = rooms.associate { room ->
                room.roomNumber to (recordsForMonth.any { it.roomNumber == room.roomNumber })
            }

            val used = mutableMapOf<String, Int>()
            val fees = mutableMapOf<String, Float>()
            for (room in rooms) {
                // 【*** 修正：使用已載入的資料，並從 meterRepository 獲取上期資料 ***】
                val currentRecord = recordsForMonth.find { it.roomNumber == room.roomNumber }
                val previousRecord = meterRepository.getPreviousRecord(room.roomNumber, month)

                if (currentRecord != null && previousRecord != null) {
                    val usedVal = currentRecord.meterValue - previousRecord.meterValue
                    if (usedVal >= 0) {
                        used[room.roomNumber] = usedVal

                        val fee = when (settings.mode) {
                            CalculationMode.FIXED -> {
                                usedVal * settings.fixedRate
                            }
                            CalculationMode.TIERED -> {
                                calculateTieredFee(
                                    totalUsage = usedVal,
                                    tiers = settings.tiers,
                                    numberOfMeters = rooms.size.coerceAtLeast(1)
                                ).finalFee.toFloat()
                            }
                        }
                        fees[room.roomNumber] = fee
                    }
                }
            }

            val canSave = rooms.any {
                val isLocked = lockedRoomMapForLoadedMonth[it.roomNumber] == true
                !isLocked && meterMapForLoadedMonth[it.roomNumber]?.isNotBlank() == true
            }

            _uiState.update {
                it.copy(
                    meterMap = meterMapForLoadedMonth,
                    lockedRoomMap = lockedRoomMapForLoadedMonth,
                    usedMap = used,
                    feeMap = fees,
                    canSave = canSave,
                    message = "",
                    messageType = MessageType.Info
                )
            }
        }
    }

    fun onShowSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }
    fun onDismissSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }
    fun saveSettings(settings: CalculationSettings) {
        viewModelScope.launch {
            settingsManager.saveSettings(settings)
            _uiState.update { it.copy(showSettingsDialog = false) }
            loadDataForMonth(_uiState.value.currentMonth)
        }
    }

    private fun changeMonth(newMonth: String) {
        if (newMonth != _uiState.value.currentMonth) {
            _uiState.update {
                it.copy(
                    currentMonth = newMonth,
                    showMonthPicker = false,
                    meterMap = emptyMap(),
                    lockedRoomMap = emptyMap(),
                    usedMap = emptyMap(),
                    feeMap = emptyMap(),
                    canSave = false,
                    message = "",
                    messageType = MessageType.Info
                )
            }
            loadDataForMonth(newMonth)
        } else {
            _uiState.update { it.copy(showMonthPicker = false) }
        }
    }

    fun onShowMonthPicker() {
        _uiState.update { it.copy(showMonthPicker = true) }
    }

    fun onDismissMonthPicker() {
        _uiState.update { it.copy(showMonthPicker = false) }
    }

    fun onMonthSelected(year: Int, month: Int) {
        val selectedMonth = String.format("%04d-%02d", year, month + 1)
        changeMonth(selectedMonth)
    }

    fun onPreviousMonth() {
        val cal = Calendar.getInstance()
        try {
            cal.time = monthFormatter.parse(_uiState.value.currentMonth) ?: Date()
        } catch (_: Exception) {
            cal.time = Date()
        }
        cal.add(Calendar.MONTH, -1)
        val newMonth = monthFormatter.format(cal.time)
        changeMonth(newMonth)
    }

    fun onNextMonth() {
        val cal = Calendar.getInstance()
        try {
            cal.time = monthFormatter.parse(_uiState.value.currentMonth) ?: Date()
        } catch (_: Exception) {
            cal.time = Date()
        }
        cal.add(Calendar.MONTH, 1)
        val newMonth = monthFormatter.format(cal.time)
        changeMonth(newMonth)
    }

    fun onMeterValueChange(roomNumber: String, value: String) {
        val newMeterMap = _uiState.value.meterMap.toMutableMap()
        newMeterMap[roomNumber] = value.filter { it.isDigit() }
        val canSaveNow = newMeterMap.any { (room, v) ->
            !(_uiState.value.lockedRoomMap[room] ?: false) && v.isNotBlank() && v.toIntOrNull() != null
        }
        _uiState.update { it.copy(meterMap = newMeterMap, canSave = canSaveNow) }
    }

    fun onLockToggle(roomNumber: String) {
        val newLockedMap = _uiState.value.lockedRoomMap.toMutableMap()
        val isLocked = newLockedMap[roomNumber] == true
        newLockedMap[roomNumber] = !isLocked

        _uiState.update { it.copy(lockedRoomMap = newLockedMap) }
    }

    fun saveAndCalculate() {
        viewModelScope.launch {
            val rooms = _uiState.value.roomList
            val currentMonth = _uiState.value.currentMonth
            val meterMap = _uiState.value.meterMap

            val recordsToSave = mutableListOf<ElectricMeterRecord>()
            var hasInvalidInput = false
            for (room in rooms) {
                if (_uiState.value.lockedRoomMap[room.roomNumber] != true) {
                    val meterValueStr = meterMap[room.roomNumber]
                    if (!meterValueStr.isNullOrBlank()) {
                        val v = meterValueStr.toIntOrNull()
                        if (v != null) {
                            // 【*** 修正：使用 meterRepository ***】
                            val previousRecord = meterRepository.getPreviousRecord(room.roomNumber, currentMonth)
                            if (previousRecord != null && v < previousRecord.meterValue) {
                                hasInvalidInput = true
                                _uiState.update { it.copy(message = "${room.roomNumber}房度數不可小於上期", messageType = MessageType.Error) }
                                return@launch
                            }

                            recordsToSave.add(
                                ElectricMeterRecord(
                                    // id 會由 repository 決定，這裡留空
                                    id = "",
                                    roomNumber = room.roomNumber,
                                    recordMonth = currentMonth,
                                    meterValue = v
                                )
                            )
                        } else {
                            hasInvalidInput = true
                        }
                    }
                }
            }

            if(hasInvalidInput){
                _uiState.update { it.copy(message = "請輸入有效的數字", messageType = MessageType.Error) }
                return@launch
            }

            if (recordsToSave.isNotEmpty()) {
                // 【*** 修正：使用 meterRepository ***】
                meterRepository.insertOrUpdateRecords(recordsToSave)
                _uiState.update {
                    it.copy(
                        message = "成功儲存 ${recordsToSave.size} 筆到雲端",
                        messageType = MessageType.Success
                    )
                }
                loadDataForMonth(currentMonth)
            } else {
                _uiState.update {
                    it.copy(
                        message = "沒有可儲存的新度數",
                        messageType = MessageType.Info
                    )
                }
            }
        }
    }

    private fun calculateTieredFee(
        totalUsage: Int,
        tiers: List<Pair<Double, Double>>,
        numberOfMeters: Int
    ): ElectricityFeeResult {
        val usageAsDouble = totalUsage.toDouble()
        if (usageAsDouble <= 0) {
            return ElectricityFeeResult(0.0, 0.0, 0.0, 0.0, "NoUsage")
        }

        val sharedTiers = tiers.map { (totalRange, rate) ->
            Pair(totalRange / numberOfMeters, rate)
        }

        var tieredFee = 0.0
        var remainingUsage = usageAsDouble

        for ((sharedRange, rate) in sharedTiers) {
            if (remainingUsage > 0) {
                // 【*** 修正：將 minOF 改為 kotlin.math.min ***】
                val usageInTier = min(remainingUsage, sharedRange)
                tieredFee += usageInTier * rate
                remainingUsage -= usageInTier
            } else {
                break
            }
        }

        val averageRate = if (usageAsDouble > 0) tieredFee / usageAsDouble else 0.0

        val finalFee: Double
        val calculationMethod: String

        // 費率不足 5 元以 5 元計 (假設)
        if (averageRate < 5.0) {
            finalFee = usageAsDouble * 5.0
            calculationMethod = "MinimumRate"
        } else {
            finalFee = tieredFee
            calculationMethod = "Tiered"
        }

        return ElectricityFeeResult(
            totalUsage = usageAsDouble,
            tieredFee = tieredFee,
            averageRate = averageRate,
            finalFee = finalFee,
            calculationMethod = calculationMethod
        )
    }

    data class ElectricityFeeResult(
        val totalUsage: Double,
        val tieredFee: Double,
        val averageRate: Double,
        val finalFee: Double,
        val calculationMethod: String
    )
}