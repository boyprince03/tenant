package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AdminRepository
import com.stevedaydream.tenantapp.data.AuthRepository
import com.stevedaydream.tenantapp.data.User
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    navController: NavHostController,
    adminRepository: AdminRepository,
    authRepository: AuthRepository // 傳入 AuthRepository
) {
    // 將兩個 repository 都傳給 factory
    val viewModel: UserListViewModel = viewModel(factory = UserListViewModelFactory(adminRepository, authRepository))
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 用於顯示錯誤訊息的 Toast
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("所有使用者") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        // 加入浮動按鈕以建立新使用者
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onAddNewUserClicked() }) {
                Icon(Icons.Default.Add, contentDescription = "新增使用者")
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.users.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("找不到任何使用者資料")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.users) { user ->
                    // 傳入一個 lambda 來處理點擊事件
                    UserCard(user = user, onClick = { viewModel.onUserClicked(user) })
                }
            }
        }

        // 根據 ViewModel 的狀態來決定是否顯示對話框
        if (uiState.showEditDialog) {
            UserEditDialog(
                user = uiState.editingUser,
                onDismiss = { viewModel.onDismissDialog() },
                onSave = { user, email, password ->
                    if (uiState.editingUser == null) { // 建立模式
                        viewModel.createUser(user, email, password)
                    } else { // 編輯模式
                        viewModel.updateUser(user)
                    }
                },
                onDelete = { user -> viewModel.deleteUser(user) }
            )
        }
    }
}

// 修改 UserCard，使其可以被點擊
@Composable
private fun UserCard(user: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick), // 加入 clickable modifier
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "使用者",
                        modifier = Modifier.padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = user.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = user.role.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when(user.role) {
                        "admin" -> MaterialTheme.colorScheme.error
                        "landlord" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )
            }
            Divider()
            InfoRow(label = "電話", value = user.phone.ifBlank { "未提供" })
            InfoRow(label = "身分證", value = user.idNumber.ifBlank { "未提供" })
            if (user.role == "landlord") {
                InfoRow(label = "房東序號", value = user.landlordCode ?: "無")
            }
            if (user.role == "tenant") {
                InfoRow(label = "綁定房號", value = user.boundRoomNumber ?: "未綁定")
            }
        }
    }
}

// 新增的編輯/建立對話框 Composable
@Composable
fun UserEditDialog(
    user: User?, // 若為 null，代表是新使用者
    onDismiss: () -> Unit,
    onSave: (user: User, email: String, password: String) -> Unit, // 建立時需要 email/password
    onDelete: (user: User) -> Unit
) {
    val isCreateMode = user == null
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 表單欄位的狀態
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(user?.username ?: "") }
    var phone by remember { mutableStateOf(user?.phone ?: "") }
    var idNumber by remember { mutableStateOf(user?.idNumber ?: "") }
    var role by remember { mutableStateOf(user?.role ?: "tenant") }
    val roles = listOf("tenant", "landlord", "admin")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreateMode) "新增使用者" else "編輯使用者") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCreateMode) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (登入帳號)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密碼") }, modifier = Modifier.fillMaxWidth())
                    Divider(Modifier.padding(vertical = 8.dp))
                }
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("姓名") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("電話") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = idNumber, onValueChange = { idNumber = it }, label = { Text("身分證") }, modifier = Modifier.fillMaxWidth())

                Text("角色", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth()) {
                    roles.forEach { roleOption ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { role = roleOption }) {
                            RadioButton(selected = (role == roleOption), onClick = { role = roleOption })
                            Text(text = roleOption)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val updatedUser = (user ?: User()).copy(
                    username = username,
                    phone = phone,
                    idNumber = idNumber,
                    role = role,
                    // 如果角色是房東，且是新使用者或現有使用者沒有房東序號，則產生一組新的
                    landlordCode = if (role == "landlord" && (isCreateMode || user?.landlordCode.isNullOrBlank())) {
                        UUID.randomUUID().toString().take(8).uppercase()
                    } else if (role != "landlord") {
                        null // 如果角色不是房東，確保序號為 null
                    }
                    else user?.landlordCode
                )
                onSave(updatedUser, email, password)
            }) { Text("儲存") }
        },
        dismissButton = {
            Row {
                if (!isCreateMode) {
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Icon(Icons.Default.Delete, contentDescription = "刪除") }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("確認刪除") },
            text = { Text("您確定要刪除使用者 ${user?.username} 嗎？此操作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        user?.let { onDelete(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("確定刪除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

// 為了風格一致，重複使用 InfoRow
@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

