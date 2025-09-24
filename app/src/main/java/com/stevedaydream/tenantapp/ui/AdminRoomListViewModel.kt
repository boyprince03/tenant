package com.stevedaydream.tenantapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevedaydream.tenantapp.data.AdminRepository
import com.stevedaydream.tenantapp.data.RoomEntity
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.net.Uri // 新增 import
import com.google.firebase.storage.ktx.storage // 新增 import
import com.google.firebase.ktx.Firebase // 新增 import
import kotlinx.coroutines.tasks.await // 新增 import
data class AdminRoomListUiState(
    val roomGroups: Map<User?, List<RoomEntity>> = emptyMap(),
    val allLandlords: List<User> = emptyList(), // Store all landlords for the dropdown
    val isLoading: Boolean = true,
    val error: String? = null,
    val showEditDialog: Boolean = false,
    val editingRoom: RoomEntity? = null,
    val isCreatingNew: Boolean = false
)

class AdminRoomListViewModel(private val adminRepository: AdminRepository) : ViewModel() {
    private val storage = Firebase.storage // 初始化 Firebase Storage
    private val _uiState = MutableStateFlow(AdminRoomListUiState())
    val uiState: StateFlow<AdminRoomListUiState> = _uiState.asStateFlow()

    init {
        loadAllRoomsGroupedByLandlord()
    }
    // 【*** 核心修改：修改 onSaveRoom 以處理圖片上傳 ***】
    fun onSaveRoom(room: RoomEntity, newImageUris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) } // 顯示載入中
            try {
                // 1. 上傳新圖片
                val newImageUrls = newImageUris.map { uri ->
                    uploadImage(room.id, uri)
                }

                // 2. 組合新舊圖片 URL
                val finalImageUrls = room.imageUrls + newImageUrls
                val roomToSave = room.copy(imageUrls = finalImageUrls)

                // 3. 儲存 RoomEntity (包含更新後的 URL 列表)
                if (_uiState.value.isCreatingNew) {
                    adminRepository.addRoom(roomToSave)
                } else {
                    adminRepository.updateRoom(roomToSave)
                }
                onDismissDialog()
                loadAllRoomsGroupedByLandlord() // 刷新列表
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "儲存失敗: ${e.message}", isLoading = false) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    // 【*** 新增：圖片上傳的輔助函式 ***】
    private suspend fun uploadImage(roomId: String, uri: Uri): String {
        // 建立一個獨一無二的檔案名稱
        val fileName = "image_${System.currentTimeMillis()}.jpg"
        // 建立在 Firebase Storage 中的儲存路徑
        val storageRef = storage.reference.child("rooms/$roomId/$fileName")

        // 上傳檔案
        storageRef.putFile(uri).await()

        // 取得下載 URL
        return storageRef.downloadUrl.await().toString()
    }

    fun loadAllRoomsGroupedByLandlord() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val allRooms = adminRepository.getAllRooms()
                val allLandlords = adminRepository.getAllLandlords()

                val landlordMap = allLandlords.associateBy { it.landlordCode }
                val groupedByLandlordCode = allRooms.groupBy { it.landlordCode }

                val finalGroups = mutableMapOf<User?, List<RoomEntity>>()
                groupedByLandlordCode.forEach { (landlordCode, rooms) ->
                    if (landlordCode == null) {
                        finalGroups[null] = rooms
                    } else {
                        val landlord = landlordMap[landlordCode]
                        if (landlord != null) {
                            finalGroups[landlord] = rooms
                        } else {
                            val unassigned = finalGroups.getOrPut(null) { emptyList() }
                            finalGroups[null] = unassigned + rooms
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        roomGroups = finalGroups,
                        allLandlords = allLandlords
                    )
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "讀取房間資料失敗: ${e.message}") }
            }
        }
    }

    // --- CRUD Event Handlers ---

    fun onAddNewRoomClicked() {
        _uiState.update {
            it.copy(
                editingRoom = RoomEntity(),
                isCreatingNew = true,
                showEditDialog = true
            )
        }
    }

    fun onEditRoomClicked(room: RoomEntity) {
        _uiState.update {
            it.copy(
                editingRoom = room,
                isCreatingNew = false,
                showEditDialog = true
            )
        }
    }

    fun onDismissDialog() {
        _uiState.update {
            it.copy(
                editingRoom = null,
                showEditDialog = false
            )
        }
    }

    fun onSaveRoom(room: RoomEntity) {
        viewModelScope.launch {
            try {
                if (_uiState.value.isCreatingNew) {
                    adminRepository.addRoom(room)
                } else {
                    adminRepository.updateRoom(room)
                }
                onDismissDialog()
                loadAllRoomsGroupedByLandlord() // Refresh list
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "儲存失敗: ${e.message}") }
            }
        }
    }

    fun onDeleteRoom(room: RoomEntity) {
        viewModelScope.launch {
            try {
                adminRepository.deleteRoom(room)
                onDismissDialog()
                loadAllRoomsGroupedByLandlord() // Refresh list
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "刪除失敗: ${e.message}") }
            }
        }
    }
}

class AdminRoomListViewModelFactory(private val adminRepository: AdminRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminRoomListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AdminRoomListViewModel(adminRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}