package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.RepairReport
import com.stevedaydream.tenantapp.data.RepairReportRepository
import com.stevedaydream.tenantapp.data.User
import com.stevedaydream.tenantapp.data.UserDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RepairViewModel(
    private val repairReportRepository: RepairReportRepository,
    private val userDao: UserDao,
    private val userId: String
) : ViewModel() {

    data class UiState(
        val currentUser: User? = null,
        val tenantName: String = "",
        val roomNumber: String = "",
        val issue: String = "",
        val description: String = "",
        val isSubmitting: Boolean = false,
        val submissionStatus: String? = null // Can be a success or error message
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        if (userId.isNotBlank()) {
            viewModelScope.launch {
                userDao.getUserById(userId).collectLatest { user ->
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            tenantName = user?.username ?: "",
                            roomNumber = user?.boundRoomNumber ?: ""
                        )
                    }
                }
            }
        }
    }

    fun onIssueChanged(newIssue: String) {
        _uiState.update { it.copy(issue = newIssue, submissionStatus = null) }
    }

    fun onDescriptionChanged(newDescription: String) {
        _uiState.update { it.copy(description = newDescription, submissionStatus = null) }
    }

    fun submitReport() {
        if (_uiState.value.tenantName.isBlank() ||
            _uiState.value.roomNumber.isBlank() ||
            _uiState.value.issue.isBlank() ||
            _uiState.value.description.isBlank()) {
            _uiState.update { it.copy(submissionStatus = "請填寫所有欄位") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submissionStatus = null) }
            try {
                val report = RepairReport(
                    tenantName = _uiState.value.tenantName,
                    roomNumber = _uiState.value.roomNumber,
                    issue = _uiState.value.issue,
                    description = _uiState.value.description,
                    userId = userId // Store the userId with the report
                )
                repairReportRepository.insert(report)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionStatus = "回報成功！",
                        issue = "", // Clear fields on success
                        description = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submissionStatus = "回報失敗: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearSubmissionStatus() {
        _uiState.update { it.copy(submissionStatus = null) }
    }
}