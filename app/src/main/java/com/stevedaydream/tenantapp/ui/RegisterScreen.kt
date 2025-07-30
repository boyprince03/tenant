package com.stevedaydream.tenantapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.stevedaydream.tenantapp.data.User
import com.stevedaydream.tenantapp.data.UserDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID




@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    userDao: UserDao
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("tenant") }
    var errorMsg by remember { mutableStateOf("") }




    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("註冊", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("帳號") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密碼") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(value = confirmPwd, onValueChange = { confirmPwd = it }, label = { Text("確認密碼") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("電話") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = idNumber, onValueChange = { idNumber = it }, label = { Text("身分證字號") }, modifier = Modifier.fillMaxWidth())
        Row {
            RadioButton(selected = role == "tenant", onClick = { role = "tenant" })
            Text("租客", Modifier.padding(end = 16.dp))
            RadioButton(selected = role == "landlord", onClick = { role = "landlord" })
            Text("房東")
        }
        if (errorMsg.isNotBlank()) Text(errorMsg, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                if (username.isBlank() || password.isBlank() || confirmPwd.isBlank()) {
                    errorMsg = "請填寫完整"
                    return@Button
                }
                if (password != confirmPwd) {
                    errorMsg = "密碼不一致"
                    return@Button
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val exists = userDao.findByUsername(username)
                    if (exists != null) {
                        withContext(Dispatchers.Main) { errorMsg = "帳號已存在" }
                    } else {
                        // 在房東註冊時自動產生 landlordCode
                        val landlordCode = if (role == "landlord") UUID.randomUUID().toString().take(8).uppercase() else null
                        userDao.insert(User(
                            username = username,
                            password = password,
                            phone = phone,
                            idNumber = idNumber,
                            role = role,
                            landlordCode = landlordCode
                        ))
                        if (role == "landlord" && landlordCode != null) {
                            withContext(Dispatchers.Main) {
                                // 彈窗提示房東序號
                                Toast.makeText(context, "註冊成功！你的房東序號：$landlordCode", Toast.LENGTH_LONG).show()
                                onRegisterSuccess()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onRegisterSuccess()
                            }
                        }

                    }

                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("註冊") }
    }
}
