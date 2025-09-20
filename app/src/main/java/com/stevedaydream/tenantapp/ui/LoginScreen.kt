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
import kotlinx.coroutines.launch
import java.util.UUID

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

    var showRoleSelectionDialog by remember { mutableStateOf(false) }
    var newUserFromGoogle by remember { mutableStateOf<User?>(null) }

    val webClientId = "303515436841-dg541bkfnkrdqip1rvs9tp2fnqhm929j.apps.googleusercontent.com"

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    val idToken = account.idToken!!
                    isLoading = true
                    scope.launch { // 【*** 修正點 1：直接在UI協程中啟動 ***】
                        try {
                            val signInResult = authRepository.loginWithGoogle(idToken)
                            if (signInResult.isNewUser) {
                                newUserFromGoogle = signInResult.user
                                showRoleSelectionDialog = true
                                isLoading = false
                            } else {
                                onLoginSuccess(signInResult.user)
                            }
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Google 登入失敗"
                            isLoading = false
                        }
                    }
                } catch (e: ApiException) {
                    errorMsg = "Google 登入失敗，請檢查網路連線或 SHA-1 設定。錯誤碼: ${e.statusCode}"
                    isLoading = false
                }
            } else {
                Toast.makeText(context, "已取消 Google 登入", Toast.LENGTH_SHORT).show()
            }
        }
    )

    if (showRoleSelectionDialog && newUserFromGoogle != null) {
        RoleSelectionDialog(
            user = newUserFromGoogle!!,
            onDismiss = { showRoleSelectionDialog = false },
            onConfirm = { userWithRole ->
                showRoleSelectionDialog = false
                isLoading = true
                scope.launch { // 【*** 修正點 2：直接在UI協程中啟動 ***】
                    try {
                        authRepository.completeGoogleRegistration(userWithRole)
                        val successMsg = if (userWithRole.role == "landlord") {
                            "註冊成功！您的房東序號是: ${userWithRole.landlordCode}"
                        } else {
                            "註冊成功！"
                        }
                        Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                        onLoginSuccess(userWithRole)
                    } catch (e: Exception) {
                        errorMsg = "註冊失敗: ${e.message}"
                        isLoading = false
                    }
                }
            }
        )
    }

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
                Button(
                    onClick = {
                        isLoading = true
                        errorMsg = ""
                        scope.launch { // 【*** 修正點 3：直接在UI協程中啟動 ***】
                            try {
                                val user = authRepository.login(email, password)
                                onLoginSuccess(user)
                            } catch (e: Exception) {
                                errorMsg = "帳號或密碼錯誤，請重試。"
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("登入") }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        errorMsg = ""
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("使用 Google 帳號登入")
                }
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

@Composable
private fun RoleSelectionDialog(
    user: User,
    onDismiss: () -> Unit,
    onConfirm: (User) -> Unit
) {
    var selectedRole by remember { mutableStateOf("tenant") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("請選擇您的身份") },
        text = {
            Column {
                Text("歡迎， ${user.username}！這是您第一次登入，請選擇您的身份以完成註冊。")
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    RadioButton(selected = selectedRole == "tenant", onClick = { selectedRole = "tenant" })
                    Text("我是租客", Modifier.padding(end = 16.dp))
                    RadioButton(selected = selectedRole == "landlord", onClick = { selectedRole = "landlord" })
                    Text("我是房東")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalUser = user.copy(
                    role = selectedRole,
                    landlordCode = if (selectedRole == "landlord") {
                        UUID.randomUUID().toString().take(8).uppercase()
                    } else {
                        null
                    }
                )
                onConfirm(finalUser)
            }) {
                Text("確認")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
