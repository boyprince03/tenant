package com.stevedaydream.tenantapp.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class UploadTestUiState(
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val isError: Boolean = false,
    val downloadUrl: String? = null
)

class UploadTestViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UploadTestUiState())
    val uiState: StateFlow<UploadTestUiState> = _uiState.asStateFlow()

    private val storage = Firebase.storage

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, progress = 0f, message = "準備上傳...", isError = false, downloadUrl = null) }
            uploadFile(uri)
        }
    }

    private suspend fun uploadFile(uri: Uri) {
        try {
            val fileName = UUID.randomUUID().toString() + ".${uri.lastPathSegment?.split(".")?.last()}"
            val storageRef = storage.reference.child("test_uploads/$fileName")

            val uploadTask = storageRef.putFile(uri)

            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount
                _uiState.update { it.copy(progress = progress.toFloat() / 100f) }
            }.await()

            val downloadUrl = storageRef.downloadUrl.await().toString()
            _uiState.update {
                it.copy(
                    isUploading = false,
                    message = "上傳成功！",
                    isError = false,
                    downloadUrl = downloadUrl
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isUploading = false,
                    message = "上傳失敗: ${e.message}",
                    isError = true
                )
            }
        }
    }
}

class UploadTestViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UploadTestViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UploadTestViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}