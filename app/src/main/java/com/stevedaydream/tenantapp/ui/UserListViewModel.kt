package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.AdminRepository
import com.stevedaydream.tenantapp.data.AuthRepository
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserListUiState(
    val users: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val editingUser: User? = null, // 追蹤正在編輯的使用者，若為 null 則代表是新使用者
    val showEditDialog: Boolean = false, // 控制對話框的顯示與否
    val error: String? = null
)

// 需要 AuthRepository 來建立新使用者的帳號密碼
class UserListViewModel(
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserListUiState())
    val uiState: StateFlow<UserListUiState> = _uiState.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val users = adminRepository.getAllUsers()
            _uiState.update {
                it.copy(
                    users = users,
                    isLoading = false
                )
            }
        }
    }

    fun onUserClicked(user: User) {
        _uiState.update { it.copy(editingUser = user, showEditDialog = true, error = null) }
    }

    fun onAddNewUserClicked() {
        _uiState.update { it.copy(editingUser = null, showEditDialog = true, error = null) }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(showEditDialog = false, editingUser = null, error = null) }
    }

    // 建立新使用者 (需要 email 和 password)
    fun createUser(user: User, email: String, password: String) {
        viewModelScope.launch {
            try {
                // 使用 AuthRepository 在 Firebase Auth 和 Firestore 中建立使用者
                authRepository.register(user, email, password)
                _uiState.update { it.copy(showEditDialog = false, editingUser = null) }
                loadUsers() // 重新整理列表
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "建立失敗") }
            }
        }
    }

    // 更新現有使用者
    fun updateUser(user: User) {
        viewModelScope.launch {
            try {
                adminRepository.updateUser(user)
                _uiState.update { it.copy(showEditDialog = false, editingUser = null) }
                loadUsers() // 重新整理列表
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "更新失敗") }
            }
        }
    }

    // 刪除使用者
    fun deleteUser(user: User) {
        viewModelScope.launch {
            try {
                adminRepository.deleteUser(user)
                _uiState.update { it.copy(showEditDialog = false, editingUser = null) }
                loadUsers() // 重新整理列表
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "刪除失敗") }
            }
        }
    }
}

// 更新 ViewModel Factory 以接收 AuthRepository
class UserListViewModelFactory(
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserListViewModel(adminRepository, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

