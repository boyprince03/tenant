package com.stevedaydream.tenantapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.User
import com.stevedaydream.tenantapp.data.UserDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (User) -> Unit,
    onNavigateRegister: () -> Unit,
    userDao: UserDao,
    navController: NavHostController
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登入") },
                // 【核心修改】將按鈕移至 navigationIcon，並修改點擊事件
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigate("visitor_home") {
                            // 清除返回堆疊，避免回到登入頁
                            popUpTo(navController.graph.findStartDestination().id)
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回首頁")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding) // 使用 Scaffold 提供的 padding
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // 移除 headline，因為 TopAppBar 已經有標題
            OutlinedTextField(
                value = username, onValueChange = { username = it }, label = { Text("帳號") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("密碼") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation()
            )
            if (errorMsg.isNotBlank()) Text(errorMsg, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        val user = userDao.login(username, password)
                        if (user != null) {
                            withContext(Dispatchers.Main) { onLoginSuccess(user) }
                        } else {
                            withContext(Dispatchers.Main) { errorMsg = "帳號或密碼錯誤" }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("登入") }
            OutlinedButton(
                onClick = onNavigateRegister, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("註冊新帳號") }
        }
    }
}