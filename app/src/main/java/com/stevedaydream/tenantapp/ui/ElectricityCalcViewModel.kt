import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.ElectricMeterDao
import com.stevedaydream.tenantapp.data.ElectricMeterRecord
import com.stevedaydream.tenantapp.data.RoomDao
import com.stevedaydream.tenantapp.data.RoomEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ElectricityCalcViewModel(
    private val roomDao: RoomDao,
    private val meterDao: ElectricMeterDao
) : ViewModel() {

    // 1. UI State
    data class UiState(
        val currentMonth: String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()),
        val showMonthPicker: Boolean = false,
        val roomList: List<RoomEntity> = emptyList(),
        val meterMap: Map<String, String> = emptyMap(),
        val lockedRoomMap: Map<String, Boolean> = emptyMap(),
        val usedMap: Map<String, Int> = emptyMap(),
        val feeMap: Map<String, Float> = emptyMap(),
        val canSave: Boolean = false,
        val message: String = "",
        val messageType: MessageType = MessageType.Info
    )

    enum class MessageType { Success, Error, Info }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val ELECTRICITY_RATE = 5.0f

    init {
        // 監聽房間資料變動並在啟動時載入
        viewModelScope.launch {
            roomDao.getAllRooms().collect { rooms ->
                _uiState.update { it.copy(roomList = rooms) }
                // 房間列表變動時，重新載入當前月份資料
                loadInitialDataForCurrentMonth(_uiState.value.currentMonth)
            }
        }
    }

    /**
     * 載入指定月份的資料庫紀錄和計算結果。
     * 此函式只在 ViewModel 啟動或成功儲存後呼叫。
     *
     * @param month 要載入的月份，格式為 "yyyy-MM"。
     */
    private fun loadInitialDataForCurrentMonth(month: String) {
        viewModelScope.launch {
            val rooms = _uiState.value.roomList
            val records = rooms.mapNotNull { room -> meterDao.getRecord(room.roomNumber, month) }

            // 根據資料庫紀錄初始化 meterMap 和 lockedRoomMap
            val meterMapForLoadedMonth = rooms.associate { room ->
                val record = records.find { it.roomNumber == room.roomNumber }
                room.roomNumber to (record?.meterValue?.toString() ?: "")
            }.toMutableMap()
            val lockedRoomMapForLoadedMonth = rooms.associate { room ->
                room.roomNumber to (records.any { it.roomNumber == room.roomNumber })
            }.toMutableMap()

            // 計算用電度數和費用
            val used = mutableMapOf<String, Int>()
            val fees = mutableMapOf<String, Float>()
            rooms.forEach { room ->
                val lastTwo = meterDao.getLastTwoRecords(room.roomNumber)
                if (lastTwo.size >= 2) {
                    val curr = lastTwo.find { it.recordMonth == month }?.meterValue
                    val prev = lastTwo.find { it.recordMonth != month }?.meterValue
                    if (curr != null && prev != null) {
                        val usedVal = curr - prev
                        used[room.roomNumber] = usedVal
                        fees[room.roomNumber] = usedVal * ELECTRICITY_RATE
                    }
                }
            }

            // 判斷是否可儲存
            val canSave = rooms.any {
                val isLocked = lockedRoomMapForLoadedMonth[it.roomNumber] == true
                !isLocked && meterMapForLoadedMonth[it.roomNumber]?.toIntOrNull() != null
            }

            _uiState.update {
                it.copy(
                    currentMonth = month,
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

    /**
     * 更新月份狀態並重置 UI 輸入。
     * 此函式用於處理月份切換，不觸發資料庫載入。
     *
     * @param newMonth 新的月份，格式為 "yyyy-MM"。
     */
    private fun _updateMonthAndResetState(newMonth: String) {
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
        } else {
            _uiState.update { it.copy(showMonthPicker = false) }
        }
    }

    // 2. 月份選擇事件
    fun onShowMonthPicker() {
        _uiState.update { it.copy(showMonthPicker = true) }
    }

    fun onDismissMonthPicker() {
        _uiState.update { it.copy(showMonthPicker = false) }
    }

    fun onMonthSelected(year: Int, month: Int) {
        val selectedMonth = String.format("%04d-%02d", year, month + 1)
        _updateMonthAndResetState(selectedMonth)
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
        _updateMonthAndResetState(newMonth)
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
        _updateMonthAndResetState(newMonth)
    }

    // 3. 輸入/鎖定事件
    fun onMeterValueChange(roomNumber: String, value: String) {
        val newMeterMap = _uiState.value.meterMap.toMutableMap()
        newMeterMap[roomNumber] = value
        val canSave = newMeterMap.any { (_, v) -> v.toIntOrNull() != null && v.isNotBlank() }
        _uiState.update { it.copy(meterMap = newMeterMap, canSave = canSave) }
    }

    fun onLockToggle(roomNumber: String) {
        val newLockedMap = _uiState.value.lockedRoomMap.toMutableMap()
        val isLocked = newLockedMap[roomNumber] == true
        newLockedMap[roomNumber] = !isLocked

        val newMeterMap = _uiState.value.meterMap.toMutableMap()
        if (isLocked) { // 如果是從鎖定變為解鎖
            newMeterMap[roomNumber] = "" // 清空輸入，讓用戶重新輸入
        }
        val canSave = newMeterMap.any { (_, v) -> v.toIntOrNull() != null && v.isNotBlank() }
        _uiState.update {
            it.copy(
                lockedRoomMap = newLockedMap,
                meterMap = newMeterMap,
                canSave = canSave
            )
        }
    }

    // 4. 儲存並計算
    fun saveAndCalculate() {
        viewModelScope.launch {
            val rooms = _uiState.value.roomList
            val currentMonth = _uiState.value.currentMonth
            val meterMap = _uiState.value.meterMap

            val recordsToSave = mutableListOf<ElectricMeterRecord>()
            rooms.forEach { room ->
                if (_uiState.value.lockedRoomMap[room.roomNumber] == true) return@forEach
                val meterValueStr = meterMap[room.roomNumber]
                val v = meterValueStr?.toIntOrNull()
                if (v != null && meterValueStr.isNotBlank()) {
                    recordsToSave.add(
                        ElectricMeterRecord(
                            roomNumber = room.roomNumber,
                            recordMonth = currentMonth,
                            meterValue = v
                        )
                    )
                }
            }

            if (recordsToSave.isNotEmpty()) {
                meterDao.insertOrUpdateRecords(recordsToSave)
                _uiState.update {
                    it.copy(
                        message = "成功儲存${recordsToSave.size}筆",
                        messageType = MessageType.Success
                    )
                }
                // 儲存成功後，重新載入該月份資料以更新預覽區塊和鎖定狀態
                loadInitialDataForCurrentMonth(currentMonth)
            } else {
                _uiState.update {
                    it.copy(
                        message = "請輸入有效數字",
                        messageType = MessageType.Error
                    )
                }
            }
        }
    }
}