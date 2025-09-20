// tenantapp/ui/LoginScreen.kt

package com.stevedaydream.tenantapp.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.stevedaydream.tenantapp.data.AuthRepository
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (User) -> Unit,
    onNavigateRegister: () -> Unit,
    authRepository: AuthRepository,
    navController: NavHostController
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // --- 【*** 核心修改 1：設定 Google 登入 ***】 ---
    // 從 google-services.json 取得 Web Client ID
    // 提示：通常可以在 R.string.default_web_client_id 中找到，如果沒有，請手動從 json 檔案複製
    val webClientId = "303515436841-dg541bkfnkrdqip1rvs9tp2fnqhm929j.apps.googleusercontent.com" // <--- !! 請務必替換成您自己的 Web Client ID !!

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // 設定一個 launcher 來接收 Google 登入 Activity 的回傳結果
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    val idToken = account.idToken!!
                    isLoading = true
                    // 拿著 idToken 去跟我們的 AuthRepository 溝通
                    scope.launch(Dispatchers.IO) {
                        try {
                            val user = authRepository.loginWithGoogle(idToken)
                            withContext(Dispatchers.Main) {
                                onLoginSuccess(user)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                errorMsg = e.message ?: "Google 登入失敗"
                                isLoading = false
                            }
                        }
                    }
                } catch (e: ApiException) {
                    errorMsg = "Google 登入失敗: ${e.statusCode}"
                }
            } else {
                Toast.makeText(context, "已取消 Google 登入", Toast.LENGTH_SHORT).show()
            }
        }
    )
    // --- 【修改結束】 ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登入") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigate("visitor_home") {
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
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email 帳號") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !isLoading
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密碼") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isLoading
            )
            if (errorMsg.isNotBlank()) Text(errorMsg, color = MaterialTheme.colorScheme.error)

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                // Email/密碼登入按鈕
                Button(
                    onClick = {
                        isLoading = true
                        errorMsg = ""
                        scope.launch(Dispatchers.IO) {
                            try {
                                val user = authRepository.login(email, password)
                                withContext(Dispatchers.Main) {
                                    onLoginSuccess(user)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMsg = "帳號或密碼錯誤，請重試。"
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("登入") }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Spacer(modifier = Modifier.height(8.dp))

                // --- 【*** 核心修改 2：新增 Google 登入按鈕 ***】 ---
                OutlinedButton(
                    onClick = {
                        errorMsg = ""
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 您可以加入 Google 的 Logo 圖示讓按鈕更好看
                    // Icon(painter = painterResource(id = R.drawable.ic_google_logo), contentDescription = "Google aign in")
                    Text("使用 Google 帳號登入")
                }
                // --- 【修改結束】 ---
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onNavigateRegister,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = !isLoading
            ) { Text("還沒有帳號？點此註冊") }
        }
    }
}