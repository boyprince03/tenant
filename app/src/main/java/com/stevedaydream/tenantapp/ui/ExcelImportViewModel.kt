package com.stevedaydream.tenantapp.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.*
import jxl.Workbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * 用於管理 Excel 匯入畫面的 UI 狀態
 */
data class ExcelImportUiState(
    val previewRows: List<Map<String, String>> = emptyList(),
    val detectedType: String? = null,
    val message: String = "",
    val isLoading: Boolean = false,
    val currentUser: User? = null // 新增：用於取得 landlordCode
)

/**
 * 處理 Excel 匯入所有邏輯的 ViewModel
 */
class ExcelImportViewModel(
    private val roomRepository: RoomRepository,
    private val meterRepository: ElectricMeterRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExcelImportUiState())
    val uiState: StateFlow<ExcelImportUiState> = _uiState.asStateFlow()

    fun setCurrentUser(userId: String) {
        viewModelScope.launch {
            userRepository.getUser(userId).collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    /**
     * 解析使用者選擇的 Excel 檔案
     */
    fun parseExcelFile(context: Context, uri: Uri) {
        _uiState.update { it.copy(isLoading = true, message = "", previewRows = emptyList(), detectedType = null) }
        viewModelScope.launch {
            val result = parseExcelAndDetectType(context, uri)
            if (result != null && result.second != null) {
                _uiState.update {
                    it.copy(
                        previewRows = result.first,
                        detectedType = result.second,
                        message = if (result.first.isEmpty()) "預覽失敗，請檢查檔案格式或內容" else "預覽成功，已自動偵測為【${result.second}】資料",
                        isLoading = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        message = "檔案格式不正確或無法辨識，請下載範本確認。",
                        previewRows = emptyList(),
                        detectedType = null,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * 將預覽的資料匯入資料庫
     */
    fun importData() {
        val state = _uiState.value
        val landlordCode = state.currentUser?.landlordCode // 從 state 取得 landlordCode
        if (state.detectedType == null) return

        viewModelScope.launch {
            val resultMessage = when (state.detectedType) {
                "房間" -> importRoomData(state.previewRows, landlordCode)
                "電表" -> importMeterData(state.previewRows)
                else -> "未知的資料類型"
            }
            // 匯入成功後清空預覽
            _uiState.update { it.copy(
                message = resultMessage,
                previewRows = emptyList(),
                detectedType = null
            ) }
        }
    }

    private suspend fun importRoomData(data: List<Map<String, String>>, landlordCode: String?): String {
        val nonDuplicateData = data.filter { it["重複"] != "是" }
        return try {
            val newRooms = nonDuplicateData.mapNotNull { row ->
                val roomNo = row["房號"] ?: return@mapNotNull null
                RoomEntity(
                    roomNumber = roomNo,
                    tenantName = row["租客姓名"] ?: "",
                    type = row["房型"] ?: "",
                    note = row["備註"] ?: "",
                    rentAmount = row["租金"]?.toIntOrNull() ?: 0,
                    deposit = row["押金"]?.toIntOrNull() ?: 0,
                    status = row["房屋狀態"] ?: "可租",
                    rentStartDate = row["起租日"] ?: "",
                    rentEndDate = row["結束日"] ?: "",
                    rentDuration = row["租賃期間"] ?: "",
                    landlordCode = landlordCode // 將房間與目前房東綁定
                )
            }
            if (newRooms.isNotEmpty()) {
                newRooms.forEach { roomRepository.addRoom(it) }
            }
            val skippedCount = data.size - newRooms.size
            "成功匯入 ${newRooms.size} 筆房間資料至雲端。已跳過 $skippedCount 筆重複資料。"
        } catch (e: Exception) {
            "房間資料匯入失敗: ${e.message}"
        }
    }

    private suspend fun importMeterData(data: List<Map<String, String>>): String {
        val nonDuplicateData = data.filter { it["重複"] != "是" }
        return try {
            val newRecords = nonDuplicateData.mapNotNull { row ->
                val roomNo = row["房號"] ?: return@mapNotNull null
                val month = row["月份"] ?: return@mapNotNull null
                val value = row["度數"]?.toIntOrNull() ?: return@mapNotNull null
                ElectricMeterRecord(roomNumber = roomNo, recordMonth = month, meterValue = value)
            }
            if (newRecords.isNotEmpty()) {
                meterRepository.insertOrUpdateRecords(newRecords)
            }
            val skippedCount = data.size - newRecords.size
            "成功匯入 ${newRecords.size} 筆電表資料至雲端。已跳過 $skippedCount 筆重複資料。"
        } catch (e: Exception) {
            "電表資料匯入失敗: ${e.message}"
        }
    }

    /**
     * 【*** 核心修正 2：實作完整的解析與比對邏輯 ***】
     */
    private suspend fun parseExcelAndDetectType(context: Context, uri: Uri): Pair<List<Map<String, String>>, String?>? = withContext(
        Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val workbook = Workbook.getWorkbook(inputStream)
            val sheet = workbook.getSheet(0) ?: return@withContext null

            if (sheet.rows < 1) return@withContext null

            val headers = (0 until sheet.columns).map { sheet.getCell(it, 0).contents }
            val detectedType = when {
                headers.contains("租金") && headers.contains("房號") -> "房間"
                headers.contains("度數") && headers.contains("房號") -> "電表"
                else -> null
            } ?: return@withContext null

            val data = (1 until sheet.rows).map { r ->
                headers.mapIndexed { c, header ->
                    header to sheet.getCell(c, r).contents
                }.toMap().toMutableMap() // Use MutableMap
            }.toMutableList()

            // 檢查重複
            if (detectedType == "房間") {
                val existingRooms = roomRepository.getRoomsForLandlord(_uiState.value.currentUser?.landlordCode ?: "").firstOrNull() ?: emptyList()
                val existingRoomNumbers = existingRooms.map { it.roomNumber }.toSet()
                data.forEach { row ->
                    row["重複"] = if (existingRoomNumbers.contains(row["房號"])) "是" else "否"
                }
            } else if (detectedType == "電表") {
                // 對於電表，重複的定義是 "房號" + "月份"
                data.forEach { row ->
                    val roomNo = row["房號"]
                    val month = row["月份"]
                    if (roomNo != null && month != null) {
                        val existingRecord = meterRepository.getRecord(roomNo, month)
                        row["重複"] = if (existingRecord != null) "是" else "否"
                    } else {
                        row["重複"] = "否" // Missing key info
                    }
                }
            }


            workbook.close()
            Pair(data, detectedType)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            inputStream?.close()
        }
    }
}

/**
 * 用於建立 ExcelImportViewModel 的工廠
 */
class ExcelImportViewModelFactory(
    private val roomRepository: RoomRepository,
    private val meterRepository: ElectricMeterRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExcelImportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExcelImportViewModel(roomRepository, meterRepository, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
