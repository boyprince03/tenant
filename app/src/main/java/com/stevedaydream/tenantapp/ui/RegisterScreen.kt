// tenantapp/ui/RegisterScreen.kt

package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions // 【*** 修正：新增 import ***】
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AuthRepository
import com.stevedaydream.tenantapp.data.User
import com.stevedaydream.tenantapp.data.UserDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    authRepository: AuthRepository,
    navController: NavHostController
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("tenant") }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("註冊新帳號") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        ) }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (作為登入帳號)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密碼 (至少6位數)") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = confirmPwd,
                onValueChange = { confirmPwd = it },
                label = { Text("確認密碼") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("您的姓名 (顯示用)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("電話") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = idNumber,
                onValueChange = { idNumber = it },
                label = { Text("身分證字號") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = role == "tenant", onClick = { role = "tenant" })
                Text("我是租客", Modifier.padding(end = 16.dp))
                RadioButton(selected = role == "landlord", onClick = { role = "landlord" })
                Text("我是房東")
            }
            if (errorMsg.isNotBlank()) Text(errorMsg, color = MaterialTheme.colorScheme.error)

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank() || username.isBlank()) {
                            errorMsg = "Email、密碼及姓名為必填欄位"
                            return@Button
                        }
                        if (password != confirmPwd) {
                            errorMsg = "兩次輸入的密碼不一致"
                            return@Button
                        }
                        isLoading = true
                        errorMsg = ""

                        scope.launch(Dispatchers.IO) {
                            try {
                                val landlordCode =
                                    if (role == "landlord") UUID.randomUUID().toString().take(8).uppercase()
                                    else null

                                val userToRegister = User(
                                    username = username,
                                    phone = phone,
                                    idNumber = idNumber,
                                    role = role,
                                    landlordCode = landlordCode
                                )
                                val registeredUser = authRepository.register(userToRegister, email, password)
                                withContext(Dispatchers.Main) {
                                    val successMsg = if (registeredUser.role == "landlord") {
                                        "註冊成功！您的房東序號是: ${registeredUser.landlordCode}"
                                    } else {
                                        "註冊成功！"
                                    }
                                    Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                                    onRegisterSuccess()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMsg = e.message ?: "註冊失敗，請稍後再試"
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("確認註冊") }
            }
        }
    }
}