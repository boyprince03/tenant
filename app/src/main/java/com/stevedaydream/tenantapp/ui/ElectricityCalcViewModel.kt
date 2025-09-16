// ui/ElectricityCalcViewModel.kt
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

    // 1. UI State (保持不變)
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
                loadDataForMonth(_uiState.value.currentMonth)
            }
        }
    }

    /**
     * 【核心修正】載入指定月份的資料庫紀錄和計算結果。
     *
     * @param month 要載入的月份，格式為 "yyyy-MM"。
     */
    private fun loadDataForMonth(month: String) {
        viewModelScope.launch {
            val rooms = _uiState.value.roomList
            // 取得本月已儲存的紀錄
            val recordsForMonth = rooms.mapNotNull { room -> meterDao.getRecord(room.roomNumber, month) }

            // 初始化UI顯示的度數和鎖定狀態
            val meterMapForLoadedMonth = rooms.associate { room ->
                val record = recordsForMonth.find { it.roomNumber == room.roomNumber }
                room.roomNumber to (record?.meterValue?.toString() ?: "")
            }.toMutableMap()
            val lockedRoomMapForLoadedMonth = rooms.associate { room ->
                room.roomNumber to (recordsForMonth.any { it.roomNumber == room.roomNumber })
            }

            // 【邏輯修正】精確計算用電度數和費用
            val used = mutableMapOf<String, Int>()
            val fees = mutableMapOf<String, Float>()
            for (room in rooms) {
                val currentRecord = recordsForMonth.find { it.roomNumber == room.roomNumber }
                // 使用新的DAO方法精確查找上個月的紀錄
                val previousRecord = meterDao.getPreviousRecord(room.roomNumber, month)

                if (currentRecord != null && previousRecord != null) {
                    val usedVal = currentRecord.meterValue - previousRecord.meterValue
                    if(usedVal >= 0){
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
     * 【流程優化】更新月份狀態並立即重新載入資料。
     */
    private fun changeMonth(newMonth: String) {
        if (newMonth != _uiState.value.currentMonth) {
            _uiState.update {
                it.copy(
                    currentMonth = newMonth,
                    showMonthPicker = false,
                    // 重置輸入與計算結果，準備顯示新月份的資料
                    meterMap = emptyMap(),
                    lockedRoomMap = emptyMap(),
                    usedMap = emptyMap(),
                    feeMap = emptyMap(),
                    canSave = false,
                    message = "",
                    messageType = MessageType.Info
                )
            }
            // 立即為新月份載入資料
            loadDataForMonth(newMonth)
        } else {
            _uiState.update { it.copy(showMonthPicker = false) }
        }
    }


    // 2. 月份選擇事件 (UI觸發)
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

    // 3. 輸入/鎖定事件 (保持不變)
    fun onMeterValueChange(roomNumber: String, value: String) {
        val newMeterMap = _uiState.value.meterMap.toMutableMap()
        newMeterMap[roomNumber] = value.filter { it.isDigit() } // 只允許數字
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

    // 4. 儲存並計算
    fun saveAndCalculate() {
        viewModelScope.launch {
            val rooms = _uiState.value.roomList
            val currentMonth = _uiState.value.currentMonth
            val meterMap = _uiState.value.meterMap

            val recordsToSave = mutableListOf<ElectricMeterRecord>()
            var hasInvalidInput = false
            rooms.forEach { room ->
                // 只處理未鎖定的房間
                if (_uiState.value.lockedRoomMap[room.roomNumber] != true) {
                    val meterValueStr = meterMap[room.roomNumber]
                    if (!meterValueStr.isNullOrBlank()) {
                        val v = meterValueStr.toIntOrNull()
                        if (v != null) {
                            // 【邏輯修正】檢查度數是否小於上期
                            val previousRecord = meterDao.getPreviousRecord(room.roomNumber, currentMonth)
                            if (previousRecord != null && v < previousRecord.meterValue) {
                                hasInvalidInput = true
                                // 可以在此處設定更詳細的錯誤訊息
                                _uiState.update { it.copy(message = "${room.roomNumber}房度數不可小於上期", messageType = MessageType.Error) }
                                return@launch
                            }

                            recordsToSave.add(
                                ElectricMeterRecord(
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
                meterDao.insertOrUpdateRecords(recordsToSave)
                _uiState.update {
                    it.copy(
                        message = "成功儲存 ${recordsToSave.size} 筆",
                        messageType = MessageType.Success
                    )
                }
                // 儲存成功後，重新載入該月份資料以更新預覽區塊和鎖定狀態
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
}